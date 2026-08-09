package com.deeptrust.media.dto;

import java.time.Instant;

public record MediaUploadResponse(
        Long mediaId,
        String sha256Hash,
        String perceptualHash,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        Instant uploadedAt
) {}
