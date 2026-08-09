package com.deeptrust.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_seq", columnList = "sequenceNumber", unique = true)
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Monotonic chain position, distinct from the DB auto-increment id so
    // ordering is explicit and independently verifiable.
    @Column(nullable = false, unique = true)
    private Long sequenceNumber;

    @Column(nullable = false)
    private Long actorUserId; // 0L reserved for system/anonymous security events

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 100)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String details;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 64)
    private String prevEntryHash;

    @Column(nullable = false, length = 64)
    private String entryHash;
}
