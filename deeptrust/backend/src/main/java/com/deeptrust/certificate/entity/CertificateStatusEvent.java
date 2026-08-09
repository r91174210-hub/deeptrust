package com.deeptrust.certificate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Append-only event log for certificate status changes.
 *
 * Blockchains (and this table, by convention) cannot "delete" or mutate
 * history — a REVOKED certificate is modeled as a NEW event appended after
 * the original ISSUED event, never as an in-place update. Certificate.status
 * is a denormalized projection of the latest event here, kept for fast reads.
 */
@Entity
@Table(name = "certificate_status_events")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long certificateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateStatus newStatus;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Long actorUserId;

    // Populated once this status change is anchored as its own blockchain
    // event/transaction (e.g. CertificateStatusChanged(...) in the contract).
    private String blockchainTxHash;

    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
