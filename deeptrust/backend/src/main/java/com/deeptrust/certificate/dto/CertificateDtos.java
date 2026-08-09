package com.deeptrust.certificate.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class CertificateDtos {

    public record CertificateResponse(
            Long id,
            String certificateCode,
            Long mediaId,
            Long analysisId,
            String status,
            String blockchainTxHash,
            Instant createdAt
    ) {}

    public record RevokeRequest(
            @NotBlank String reason
    ) {}

    public record RevokeResponse(
            String certificateCode,
            String status,
            String reason,
            String blockchainTxHash,
            Instant occurredAt
    ) {}
}
