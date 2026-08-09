package com.deeptrust.audit.controller;

import com.deeptrust.audit.dto.ChainVerificationResult;
import com.deeptrust.audit.service.AuditChainVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditChainVerificationService verificationService;

    /** Recomputes the entire audit hash chain and reports the first broken link, if any. */
    @GetMapping("/verify-chain")
    @PreAuthorize("hasRole('ROOT')")
    public ChainVerificationResult verifyChain() {
        return verificationService.verifyFullChain();
    }
}
