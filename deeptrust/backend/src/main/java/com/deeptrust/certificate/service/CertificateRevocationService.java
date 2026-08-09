package com.deeptrust.certificate.service;

import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.blockchain.BlockchainAnchorService;
import com.deeptrust.blockchain.BlockchainAnchorService.AnchorResult;
import com.deeptrust.certificate.dto.CertificateDtos.RevokeResponse;
import com.deeptrust.certificate.entity.Certificate;
import com.deeptrust.certificate.entity.CertificateStatus;
import com.deeptrust.certificate.entity.CertificateStatusEvent;
import com.deeptrust.certificate.repository.CertificateRepository;
import com.deeptrust.certificate.repository.CertificateStatusEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles certificate revocation as an APPENDED event, never an in-place
 * mutation or delete — consistent with the immutable-ledger philosophy of
 * the whole system. The original CertificateIssued record (both in MySQL's
 * certificate_status_events table and on-chain) remains permanently intact
 * and queryable; revocation only adds a new event on top of it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateRevocationService {

    private final CertificateRepository certificateRepository;
    private final CertificateStatusEventRepository statusEventRepository;
    private final BlockchainAnchorService blockchainAnchorService;
    private final AuditLogService auditLogService;

    @Transactional
    public RevokeResponse revoke(String certificateCode, String reason, Long actorUserId) {
        Certificate certificate = certificateRepository.findByCertificateCode(certificateCode)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateCode));

        if (certificate.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalStateException("Certificate " + certificateCode + " is already revoked.");
        }

        // Append the revocation event on-chain FIRST — if this fails, we
        // don't want MySQL and the chain to disagree about status.
        AnchorResult anchor = blockchainAnchorService.revokeCertificate(certificateCode, reason);

        // Denormalized projection update — the event row below is the
        // actual source of truth.
        certificate.setStatus(CertificateStatus.REVOKED);
        certificateRepository.save(certificate);

        CertificateStatusEvent event = CertificateStatusEvent.builder()
                .certificateId(certificate.getId())
                .newStatus(CertificateStatus.REVOKED)
                .reason(reason)
                .actorUserId(actorUserId)
                .blockchainTxHash(anchor.transactionHash())
                .occurredAt(Instant.now())
                .build();
        statusEventRepository.save(event);

        auditLogService.logAction(actorUserId, "CERTIFICATE_REVOKED", "Certificate", certificate.getId(),
                "code=" + certificateCode + " reason=" + reason + " tx=" + anchor.transactionHash());

        log.info("Certificate {} revoked by user {} — reason: {}", certificateCode, actorUserId, reason);

        return new RevokeResponse(
                certificateCode,
                CertificateStatus.REVOKED.name(),
                reason,
                anchor.transactionHash(),
                event.getOccurredAt()
        );
    }
}
