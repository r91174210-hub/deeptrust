package com.deeptrust.certificate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "certificates", indexes = {
        @Index(name = "idx_cert_code", columnList = "certificateCode", unique = true)
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-readable public identifier, e.g. DT-2026-000001
    @Column(nullable = false, unique = true, length = 30)
    private String certificateCode;

    @Column(nullable = false)
    private Long mediaId;

    @Column(nullable = false)
    private Long analysisId;

    @Column(nullable = false)
    private Long issuedByUserId;

    /**
     * Current status is a PROJECTION derived from the append-only
     * CertificateStatusEvent log (see certificate.entity.CertificateStatusEvent).
     * It is kept here denormalized for fast reads, but the event log is the
     * source of truth — this column is never the only record of a change.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.ACTIVE;

    // Populated once Module 5 (blockchain anchoring) confirms the transaction.
    private String blockchainTxHash;

    private Long anchoredAtBlockNumber;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
