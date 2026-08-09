package com.deeptrust.audit.repository;

import com.deeptrust.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Optional<AuditLog> findTopByOrderBySequenceNumberDesc();
    List<AuditLog> findAllByOrderBySequenceNumberAsc();
}
