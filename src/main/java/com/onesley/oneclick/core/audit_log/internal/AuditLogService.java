package com.onesley.oneclick.core.audit_log.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static com.onesley.oneclick.core.audit_log.api.AuditLogDtos.*;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.JobExecutionDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.SystemEventCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.SystemEventDto;
import com.onesley.oneclick.core.audit_log.api.SystemEvent;
import lombok.RequiredArgsConstructor;

/**
 * Service de lecture/écriture des journaux audit + events + erreurs.
 *
 * <p>La plupart de ces tables sont écrites par le système (interceptors, listeners,
 * jobs) et lues via REST pour debug/monitoring. On expose la création via API
 * principalement pour les ErrorLog côté frontend (capture JS) et pour les
 * intégrations externes qui publient des SystemEvent.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditRepo;
    private final SystemEventRepository eventRepo;
    private final ErrorLogRepository errorRepo;
    private final JobExecutionRepository jobRepo;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── AuditLog ────────────────────────────────────────────────────────────

    public Page<AuditLogDto> findAuditLogs(UUID userId, UUID tenantId, String entityType, UUID entityId,
                                            int page, int size) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (userId != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("userId"), userId));
        if (tenantId != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (entityType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        if (entityId != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId));
        return auditRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(AuditLog::toDto);
    }

    @Transactional
    public AuditLogDto recordAudit(AuditLogCreateDto dto) {
        User userRef = dto.userId() != null ? entityManager.getReference(User.class, dto.userId()) : null;
        AuditLog a = new AuditLog(UUID.randomUUID(), userRef, dto.entityType(), dto.entityId(), dto.action());
        if (dto.tenantId() != null) {
            a.setTenant(entityManager.getReference(Tenant.class, dto.tenantId()));
        }
        if (dto.diff() != null) a.getDiff().putAll(dto.diff());
        if (dto.ipAddress() != null) a.setIpAddress(dto.ipAddress());
        if (dto.userAgent() != null) a.setUserAgent(dto.userAgent());
        return auditRepo.save(a).toDto();
    }

    // ─── SystemEvent ─────────────────────────────────────────────────────────

    public Page<SystemEventDto> findEvents(String type, Boolean unprocessedOnly, int page, int size) {
        Specification<SystemEvent> spec = (root, q, cb) -> cb.conjunction();
        if (type != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        if (Boolean.TRUE.equals(unprocessedOnly)) {
            spec = spec.and((root, q, cb) -> cb.isNull(root.get("processedAt")));
        }
        return eventRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(SystemEvent::toDto);
    }

    @Transactional
    public SystemEventDto publishEvent(SystemEventCreateDto dto) {
        Map<String, Object> payload = dto.payload() != null ? dto.payload() : Map.of();
        SystemEvent e = new SystemEvent(UUID.randomUUID(), dto.type(), payload);
        return eventRepo.save(e).toDto();
    }

    // ─── ErrorLog ────────────────────────────────────────────────────────────

    public Page<ErrorLogDto> findErrors(String serviceName, String severity, int page, int size) {
        Specification<ErrorLog> spec = (root, q, cb) -> cb.conjunction();
        if (serviceName != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("serviceName"), serviceName));
        if (severity != null)    spec = spec.and((root, q, cb) -> cb.equal(root.get("severity"), severity));
        return errorRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(ErrorLog::toDto);
    }

    @Transactional
    public ErrorLogDto recordError(ErrorLogCreateDto dto) {
        String sev = dto.severity() != null ? dto.severity() : "error";
        ErrorLog e = new ErrorLog(UUID.randomUUID(), dto.serviceName(), dto.message(), sev);
        if (dto.stacktrace() != null) e.setStacktrace(dto.stacktrace());
        return errorRepo.save(e).toDto();
    }

    // ─── JobExecution (lecture seule via REST — écrite par les jobs eux-mêmes) ─

    public Page<JobExecutionDto> findJobs(String jobName, String status, int page, int size) {
        Specification<JobExecution> spec = (root, q, cb) -> cb.conjunction();
        if (jobName != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("jobName"), jobName));
        if (status != null)  spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return jobRepo.findAll(spec, PageRequest.of(page, size, Sort.by("startedAt").descending()))
            .map(JobExecution::toDto);
    }
}
