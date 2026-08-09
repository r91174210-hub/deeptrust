package com.deeptrust.verify.dto;

import java.time.Instant;

public record VerificationResult(
        boolean exactMatch,          // SHA-256 identical to registered original
        boolean perceptualMatch,     // pHash within similarity threshold (compression/resize tolerant)
        int hammingDistance,         // pHash distance, -1 if not applicable (e.g. audio-only)
        String certificateCode,
        String certificateStatus,    // ACTIVE | REVOKED | null if no certificate
        String aiResult,             // AUTHENTIC | MANIPULATED | INCONCLUSIVE
        double confidence,
        String blockchainTxHash,
        boolean chainConfirmed,      // on-chain record independently confirms MySQL record
        Instant certificateIssuedAt,
        String message
) {}
