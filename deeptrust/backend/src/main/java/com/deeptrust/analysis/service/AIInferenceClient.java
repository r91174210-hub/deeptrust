package com.deeptrust.analysis.service;

import com.deeptrust.analysis.dto.AIAnalysisResponse;
import com.deeptrust.analysis.entity.Analysis;
import com.deeptrust.analysis.entity.AnalysisResult;
import com.deeptrust.analysis.repository.AnalysisRepository;
import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.media.entity.Media;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Talks to the isolated Python AI microservice over REST. This service is
 * the ONLY component permitted to cross that trust boundary — the AI
 * container never touches MySQL or the blockchain directly, keeping the
 * inference sandbox stateless and replaceable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIInferenceClient {

    private final AnalysisRepository analysisRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Value("${deeptrust.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    private RestClient restClient() {
        return RestClient.builder().baseUrl(aiServiceBaseUrl).build();
    }

    @Transactional
    public Analysis analyze(Media media, byte[] fileBytes, Long triggeredByUserId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return media.getOriginalFilename();
            }
        });

        AIAnalysisResponse aiResponse = restClient().post()
                .uri("/api/v1/analyze")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AIAnalysisResponse.class);

        if (aiResponse == null) {
            throw new IllegalStateException("AI microservice returned an empty response for media " + media.getId());
        }

        String heatmapJson;
        try {
            heatmapJson = objectMapper.writeValueAsString(aiResponse.heatmapCoordinates());
        } catch (Exception e) {
            log.warn("Failed to serialize heatmap coordinates for media {}", media.getId(), e);
            heatmapJson = "[]";
        }

        Analysis analysis = Analysis.builder()
                .mediaId(media.getId())
                .aiResult(AnalysisResult.valueOf(aiResponse.result()))
                .confidence(aiResponse.confidence())
                .heatmapCoordinatesJson(heatmapJson)
                .modelVersion(aiResponse.modelVersion())
                .analyzedAt(Instant.now())
                .build();

        analysis = analysisRepository.save(analysis);

        auditLogService.logAction(
                triggeredByUserId,
                "AI_ANALYSIS_COMPLETE",
                "Analysis",
                analysis.getId(),
                "mediaId=" + media.getId() + " result=" + analysis.getAiResult() + " confidence=" + analysis.getConfidence()
        );

        return analysis;
    }
}
