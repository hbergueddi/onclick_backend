package com.onesley.oneclick.modules.system.api;

import com.onesley.oneclick.modules.system.internal.AppDocument;
import com.onesley.oneclick.modules.system.internal.CustomRole;
import com.onesley.oneclick.modules.system.internal.DocumentVersion;
import com.onesley.oneclick.modules.system.internal.QuotaChangeLog;
import com.onesley.oneclick.modules.system.internal.SystemAlert;
import com.onesley.oneclick.modules.system.internal.SystemAlertRule;
import com.onesley.oneclick.modules.system.internal.SystemHealthCheck;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SystemDtos {

    private SystemDtos() {}

    public record HealthCheckDto(
        UUID id, String component, String status, Integer latencyMs,
        String errorMessage, Map<String, Object> metadata, Instant checkedAt
    ) {
        public static HealthCheckDto from(SystemHealthCheck h) {
            return new HealthCheckDto(h.getId(), h.getComponent(), h.getStatus(),
                h.getLatencyMs(), h.getErrorMessage(), h.getMetadata(), h.getCheckedAt());
        }
    }

    public record AlertDto(
        UUID id, UUID ruleId, String severity, String message,
        Map<String, Object> context, Instant acknowledgedAt, UUID acknowledgedBy,
        Instant createdAt
    ) {
        public static AlertDto from(SystemAlert a) {
            return new AlertDto(a.getId(), a.getRuleId(), a.getSeverity(), a.getMessage(),
                a.getContext(), a.getAcknowledgedAt(), a.getAcknowledgedBy(), a.getCreatedAt());
        }
    }

    public record AlertRuleDto(UUID id, String name, String conditionExpr, String severity, Boolean enabled) {
        public static AlertRuleDto from(SystemAlertRule r) {
            return new AlertRuleDto(r.getId(), r.getName(), r.getConditionExpr(), r.getSeverity(), r.getEnabled());
        }
    }

    public record QuotaChangeLogDto(
        UUID id, UUID tenantId, UUID restaurantId, UUID userId,
        String quotaType, Integer oldValue, Integer newValue, String reason, Instant createdAt
    ) {
        public static QuotaChangeLogDto from(QuotaChangeLog q) {
            return new QuotaChangeLogDto(q.getId(), q.getTenantId(), q.getRestaurantId(),
                q.getUserId(), q.getQuotaType(), q.getOldValue(), q.getNewValue(),
                q.getReason(), q.getCreatedAt());
        }
    }

    // ─── V24 — Sprint K : documentation interne (DocumentExport) ─────────────

    public record AppDocumentDto(
        String id, String content, String version,
        Instant updatedAt, UUID updatedBy, Instant createdAt
    ) {
        public static AppDocumentDto from(AppDocument d) {
            return new AppDocumentDto(d.getId(), d.getContent(), d.getVersion(),
                d.getUpdatedAt(), d.getUpdatedBy(), d.getCreatedAt());
        }
    }

    /** Body PUT — upsert partiel du document courant. */
    public record AppDocumentUpsertDto(@Size(min = 1, max = 4096) String content, @Size(min = 1, max = 64) String version) {}

    public record DocumentVersionDto(
        UUID id, String documentId, String version, String content,
        String notes, UUID createdBy, Instant createdAt
    ) {
        public static DocumentVersionDto from(DocumentVersion v) {
            return new DocumentVersionDto(v.getId(), v.getDocumentId(), v.getVersion(),
                v.getContent(), v.getNotes(), v.getCreatedBy(), v.getCreatedAt());
        }
    }

    public record DocumentVersionCreateDto(
        @NotBlank String version, String content, String notes
    ) {}

    // ─── V24 — Sprint K : rôles personnalisés admin (GestionRoles) ──────────

    public record CustomRoleDto(
        UUID id, String name, String description, List<String> permissions,
        UUID createdBy, Instant createdAt, Instant updatedAt
    ) {
        public static CustomRoleDto from(CustomRole r) {
            return new CustomRoleDto(r.getId(), r.getName(), r.getDescription(),
                r.getPermissions() == null ? List.of() : List.of(r.getPermissions()),
                r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record CustomRoleCreateDto(
        @NotBlank String name, String description, List<String> permissions
    ) {}
}
