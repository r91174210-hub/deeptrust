package com.deeptrust.certificate.service;

import com.deeptrust.analysis.entity.Analysis;
import com.deeptrust.analysis.entity.AnalysisResult;
import com.deeptrust.analysis.repository.AnalysisRepository;
import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.blockchain.BlockchainAnchorService;
import com.deeptrust.blockchain.BlockchainAnchorService.AnchorResult;
import com.deeptrust.certificate.dto.CertificateDtos.CertificateResponse;
import com.deeptrust.certificate.entity.Certificate;
import com.deeptrust.certificate.entity.CertificateStatus;
import com.deeptrust.certificate.entity.CertificateStatusEvent;
import com.deeptrust.certificate.repository.CertificateRepository;
import com.deeptrust.certificate.repository.CertificateStatusEventRepository;
import com.deeptrust.media.entity.Media;
import com.deeptrust.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Year;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateStatusEventRepository statusEventRepository;
    private final MediaRepository mediaRepository;
    private final AnalysisRepository analysisRepository;
    private final BlockchainAnchorService blockchainAnchorService;
    private final AuditLogService auditLogService;

    @Transactional
    public CertificateResponse issueCertificate(Long mediaId, Long issuedByUserId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + mediaId));

        Analysis analysis = analysisRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new IllegalStateException("No analysis found for media " + mediaId + " — run AI inference first."));

        certificateRepository.findByMediaId(mediaId).ifPresent(existing -> {
            throw new IllegalStateException("Certificate already issued for this media: " + existing.getCertificateCode());
        });

        String certificateCode = generateCertificateCode();

        Certificate certificate = Certificate.builder()
                .certificateCode(certificateCode)
                .mediaId(media.getId())
                .analysisId(analysis.getId())
                .issuedByUserId(issuedByUserId)
                .status(CertificateStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        certificate = certificateRepository.save(certificate);

        // Anchor to the immutable ledger — only certificateId, sha256 hash,
        // result code, and timestamp cross this boundary. No PII, no filenames.
        AnchorResult anchor = blockchainAnchorService.issueCertificate(
                certificateCode,
                media.getSha256Hash(),
                mapResultCode(analysis.getAiResult())
        );

        certificate.setBlockchainTxHash(anchor.transactionHash());
        certificate.setAnchoredAtBlockNumber(anchor.blockNumber());
        certificate = certificateRepository.save(certificate);

        // First status event: ISSUED, mirrored on-chain by the CertificateIssued event.
        statusEventRepository.save(CertificateStatusEvent.builder()
                .certificateId(certificate.getId())
                .newStatus(CertificateStatus.ACTIVE)
                .reason("Initial issuance")
                .actorUserId(issuedByUserId)
                .blockchainTxHash(anchor.transactionHash())
                .occurredAt(Instant.now())
                .build());

        auditLogService.logAction(issuedByUserId, "CERTIFICATE_ISSUED", "Certificate", certificate.getId(),
                "code=" + certificateCode + " tx=" + anchor.transactionHash());

        return toResponse(certificate);
    }

    private BlockchainAnchorService.ResultCode mapResultCode(AnalysisResult result) {
        return switch (result) {
            case AUTHENTIC -> BlockchainAnchorService.ResultCode.AUTHENTIC;
            case MANIPULATED -> BlockchainAnchorService.ResultCode.MANIPULATED;
            case INCONCLUSIVE -> BlockchainAnchorService.ResultCode.INCONCLUSIVE;
        };
    }

    /**
     * Generates codes like DT-2026-000001, sequential within the current year.
     * Uses a DB count as a simple approach; for high-concurrency production
     * use, replace with a dedicated sequence table + SELECT ... FOR UPDATE.
     */
    private String generateCertificateCode() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        Instant yearStart = Instant.parse(year + "-01-01T00:00:00Z");
        Instant yearEnd = yearStart.plus(365, ChronoUnit.DAYS);
        long countThisYear = certificateRepository.countByCreatedAtBetween(yearStart, yearEnd);
        long nextSeq = countThisYear + 1;
        return String.format("DT-%d-%06d", year, nextSeq);
    }

    private CertificateResponse toResponse(Certificate c) {
        return new CertificateResponse(
                c.getId(), c.getCertificateCode(), c.getMediaId(), c.getAnalysisId(),
                c.getStatus().name(), c.getBlockchainTxHash(), c.getCreatedAt()
        );
    }
}
