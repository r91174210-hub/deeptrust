package com.deeptrust.media.service;

import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.media.dto.MediaUploadResponse;
import com.deeptrust.media.entity.Media;
import com.deeptrust.media.exception.DuplicateMediaException;
import com.deeptrust.media.exception.UnsupportedMediaTypeException;
import com.deeptrust.media.repository.MediaRepository;
import com.deeptrust.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final long MAX_FILE_SIZE_BYTES = 200L * 1024 * 1024; // 200MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp",
            "video/mp4", "video/quicktime",
            "audio/mpeg", "audio/wav"
    );

    private final FileHashingService fileHashingService;
    private final MediaStorageService mediaStorageService;
    private final MediaRepository mediaRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public MediaUploadResponse processUpload(MultipartFile file, Long userId) {
        validateFile(file);

        String sha256 = fileHashingService.sha256(file);

        // Exact-byte duplicate check before pHash computation or storage —
        // fail fast and avoid wasted work.
        mediaRepository.findBySha256Hash(sha256).ifPresent(existing -> {
            throw new DuplicateMediaException("Identical file already registered as media ID " + existing.getId());
        });

        String pHash = fileHashingService.perceptualHash(file);
        String storagePath = mediaStorageService.store(file, sha256);

        Media media = Media.builder()
                .sha256Hash(sha256)
                .perceptualHash(pHash)
                .originalFilename(sanitizeFilename(file.getOriginalFilename()))
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .storagePath(storagePath)
                .uploadedByUserId(userId)
                .uploadedAt(Instant.now())
                .build();

        media = mediaRepository.save(media);

        auditLogService.logAction(userId, "MEDIA_UPLOAD", "Media", media.getId(), "sha256=" + sha256);

        return new MediaUploadResponse(
                media.getId(),
                media.getSha256Hash(),
                media.getPerceptualHash(),
                media.getOriginalFilename(),
                media.getMimeType(),
                media.getFileSizeBytes(),
                media.getUploadedAt()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty or missing.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum allowed size of 200MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new UnsupportedMediaTypeException("Unsupported file type: " + contentType);
        }
        // NOTE: the Content-Type header is client-supplied and spoofable.
        // Production hardening: sniff magic bytes with Apache Tika before
        // trusting this for anything security-relevant.
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        String base = java.nio.file.Paths.get(filename).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
