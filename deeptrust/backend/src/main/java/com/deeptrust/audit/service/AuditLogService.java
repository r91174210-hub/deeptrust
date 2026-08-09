package com.deeptrust.audit.service;

import com.deeptrust.audit.entity.AuditLog;
import com.deeptrust.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditChainService auditChainService;

    /**
     * synchronized: sequenceNumber + prevEntryHash must be assigned
     * atomically relative to the last row. Two concurrent writers computing
     * "next" off a stale prevEntryHash would silently fork the chain.
     * For multi-instance production deployments, replace this with a DB-level
     * advisory lock (SELECT ... FOR UPDATE on a singleton "chain_tip" row)
     * so correctness holds across app instances, not just threads.
     */
    @Transactional
    public synchronized AuditLog logAction(Long actorUserId, String action, String entityType, Long entityId, String details) {
        return append(actorUserId, action, entityType, entityId, details, currentClientIp());
    }

    @Transactional
    public synchronized AuditLog logSecurityEvent(String action, String ipAddress, String details) {
        return append(0L, action, "Security", null, details, ipAddress != null ? ipAddress : currentClientIp());
    }

    private AuditLog append(Long actorUserId, String action, String entityType, Long entityId, String details, String ipAddress) {
        AuditLog last = auditLogRepository.findTopByOrderBySequenceNumberDesc().orElse(null);

        long nextSequence = (last == null) ? 1L : last.getSequenceNumber() + 1;
        String prevHash = (last == null) ? AuditChainService.GENESIS_HASH : last.getEntryHash();
        Instant now = Instant.now();

        String entryHash = auditChainService.computeEntryHash(
                prevHash, actorUserId, action, entityType, entityId, details, ipAddress, now.toEpochMilli());

        AuditLog entry = AuditLog.builder()
                .sequenceNumber(nextSequence)
                .actorUserId(actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .timestamp(now)
                .prevEntryHash(prevHash)
                .entryHash(entryHash)
                .build();

        return auditLogRepository.save(entry);
    }

    /** Resolves the real client IP from the current request context, honoring X-Forwarded-For behind a proxy. */
    private String currentClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "system";

            HttpServletRequest request = attrs.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
