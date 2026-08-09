package com.deeptrust.audit.service;

import com.deeptrust.audit.entity.AuditLog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class AuditChainService {

    // Fixed genesis seed — public anchor value, not a secret. Must be
    // identical across all environments for deterministic chain validation.
    public static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    public String computeEntryHash(
            String prevEntryHash,
            Long actorUserId,
            String action,
            String entityType,
            Long entityId,
            String details,
            String ipAddress,
            long epochMillis
    ) {
        String payload = String.join("|",
                prevEntryHash,
                String.valueOf(actorUserId),
                nullSafe(action),
                nullSafe(entityType),
                String.valueOf(entityId),
                nullSafe(details),
                nullSafe(ipAddress),
                String.valueOf(epochMillis)
        );
        return sha256Hex(payload);
    }

    /** Recomputes an entry's hash and compares it to what's stored — used by the verification job. */
    public boolean verifyEntry(AuditLog entry) {
        String recomputed = computeEntryHash(
                entry.getPrevEntryHash(),
                entry.getActorUserId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDetails(),
                entry.getIpAddress(),
                entry.getTimestamp().toEpochMilli()
        );
        return recomputed.equals(entry.getEntryHash());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
