package com.onesley.oneclick.modules.system.api;

import com.onesley.oneclick.modules.system.internal.QuotaChangeLog;
import com.onesley.oneclick.modules.system.internal.SystemAlert;
import com.onesley.oneclick.modules.system.internal.SystemAlertRule;
import com.onesley.oneclick.modules.system.internal.SystemHealthCheck;

import java.time.Instant;
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
}
