package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.system.api.SystemDtos.*;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SystemService {

    private final SystemHealthCheckRepository healthRepo;
    private final SystemAlertRepository alertRepo;
    private final SystemAlertRuleRepository ruleRepo;
    private final QuotaChangeLogRepository quotaRepo;
    private final AppDocumentRepository documentRepo;
    private final DocumentVersionRepository versionRepo;
    private final CustomRoleRepository customRoleRepo;

    public SystemService(
        SystemHealthCheckRepository healthRepo,
        SystemAlertRepository alertRepo,
        SystemAlertRuleRepository ruleRepo,
        QuotaChangeLogRepository quotaRepo,
        AppDocumentRepository documentRepo,
        DocumentVersionRepository versionRepo,
        CustomRoleRepository customRoleRepo
    ) {
        this.healthRepo = healthRepo;
        this.alertRepo = alertRepo;
        this.ruleRepo = ruleRepo;
        this.quotaRepo = quotaRepo;
        this.documentRepo = documentRepo;
        this.versionRepo = versionRepo;
        this.customRoleRepo = customRoleRepo;
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDto> findRecentHealthChecks(int limit) {
        return healthRepo.findRecent(PageRequest.of(0, limit)).stream().map(HealthCheckDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDto> findByComponent(String component, int limit) {
        return healthRepo.findByComponent(component, PageRequest.of(0, limit)).stream().map(HealthCheckDto::from).toList();
    }

    public HealthCheckDto recordHealthCheck(SystemHealthCheck check) {
        return HealthCheckDto.from(healthRepo.save(check));
    }

    @Transactional(readOnly = true)
    public List<AlertDto> findUnacknowledged() {
        return alertRepo.findUnacknowledged().stream().map(AlertDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> findRecentAlerts(int limit) {
        return alertRepo.findRecent(PageRequest.of(0, limit)).stream().map(AlertDto::from).toList();
    }

    public AlertDto acknowledge(UUID id, UUID acknowledgedBy) {
        SystemAlert a = alertRepo.findById(id).orElseThrow();
        a.setAcknowledgedAt(Instant.now());
        a.setAcknowledgedBy(acknowledgedBy);
        return AlertDto.from(alertRepo.save(a));
    }

    @Transactional(readOnly = true)
    public List<AlertRuleDto> findRules() {
        return ruleRepo.findAllEnabled().stream().map(AlertRuleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<QuotaChangeLogDto> findRecentQuotaLogs(int limit) {
        return quotaRepo.findRecent(PageRequest.of(0, limit)).stream().map(QuotaChangeLogDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<QuotaChangeLogDto> findQuotaLogsByRestaurant(UUID restaurantId) {
        return quotaRepo.findByRestaurant(restaurantId).stream().map(QuotaChangeLogDto::from).toList();
    }

    public QuotaChangeLogDto recordQuotaChange(QuotaChangeLog log) {
        return QuotaChangeLogDto.from(quotaRepo.save(log));
    }

    // ─── V24 — Sprint K : documentation interne ─────────────────────────────

    @Transactional(readOnly = true)
    public AppDocumentDto findDocument(String id) {
        return documentRepo.findById(id)
            .map(AppDocumentDto::from)
            .orElseThrow(() -> new NotFoundException("AppDocument", id));
    }

    /** Upsert du document courant — crée la row si absente, applique les champs non-null. */
    public AppDocumentDto upsertDocument(String id, AppDocumentUpsertDto dto) {
        AppDocument d = documentRepo.findById(id).orElseGet(() -> {
            AppDocument created = new AppDocument();
            created.setId(id);
            return created;
        });
        if (dto.content() != null) d.setContent(dto.content());
        if (dto.version() != null) d.setVersion(dto.version());
        d.setUpdatedAt(Instant.now());
        d.setUpdatedBy(SecurityHelper.currentUserId());
        return AppDocumentDto.from(documentRepo.save(d));
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionDto> findDocumentVersions(String documentId) {
        return versionRepo.findByDocument(documentId).stream().map(DocumentVersionDto::from).toList();
    }

    /** Archive une révision — la row parente {@code app_documents} doit exister. */
    public DocumentVersionDto addDocumentVersion(String documentId, DocumentVersionCreateDto dto) {
        if (!documentRepo.existsById(documentId)) {
            throw new NotFoundException("AppDocument", documentId);
        }
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(documentId);
        v.setVersion(dto.version());
        v.setContent(dto.content());
        v.setNotes(dto.notes());
        return DocumentVersionDto.from(versionRepo.save(v));
    }

    // ─── V24 — Sprint K : rôles personnalisés admin ─────────────────────────

    @Transactional(readOnly = true)
    public List<CustomRoleDto> findCustomRoles() {
        return customRoleRepo.findAllOrdered().stream().map(CustomRoleDto::from).toList();
    }

    public CustomRoleDto createCustomRole(CustomRoleCreateDto dto) {
        CustomRole r = new CustomRole();
        r.setName(dto.name());
        r.setDescription(dto.description());
        r.setPermissions(dto.permissions() == null
            ? new String[0]
            : dto.permissions().toArray(new String[0]));
        r.setCreatedBy(SecurityHelper.currentUserId());
        return CustomRoleDto.from(customRoleRepo.save(r));
    }

    public void deleteCustomRole(UUID id) {
        if (!customRoleRepo.existsById(id)) {
            throw new NotFoundException("CustomRole", id);
        }
        customRoleRepo.deleteById(id);
    }
}
