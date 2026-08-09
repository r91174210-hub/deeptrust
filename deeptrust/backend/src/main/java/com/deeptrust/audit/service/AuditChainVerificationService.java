package com.deeptrust.audit.service;

import com.deeptrust.audit.dto.ChainVerificationResult;
import com.deeptrust.audit.entity.AuditLog;
import com.deeptrust.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditChainVerificationService {

    private final AuditLogRepository auditLogRepository;
    private final AuditChainService auditChainService;

    public ChainVerificationResult verifyFullChain() {
        List<AuditLog> entries = auditLogRepository.findAllByOrderBySequenceNumberAsc();
        String expectedPrevHash = AuditChainService.GENESIS_HASH;

        for (AuditLog entry : entries) {
            // 1. Linkage check: does this entry point to the true previous hash?
            if (!entry.getPrevEntryHash().equals(expectedPrevHash)) {
                return ChainVerificationResult.broken(entry.getSequenceNumber(), "PREV_HASH_MISMATCH");
            }
            // 2. Content check: does the stored hash match a recompute of the row's own content?
            if (!auditChainService.verifyEntry(entry)) {
                return ChainVerificationResult.broken(entry.getSequenceNumber(), "ENTRY_HASH_MISMATCH");
            }
            expectedPrevHash = entry.getEntryHash();
        }

        return ChainVerificationResult.valid(entries.size());
    }
}
