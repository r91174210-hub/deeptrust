package com.deeptrust.audit.dto;

public record ChainVerificationResult(
        boolean valid,
        int entriesVerified,
        Long brokenAtSequence,
        String failureReason
) {
    public static ChainVerificationResult valid(int count) {
        return new ChainVerificationResult(true, count, null, null);
    }
    public static ChainVerificationResult broken(Long seq, String reason) {
        return new ChainVerificationResult(false, 0, seq, reason);
    }
}
