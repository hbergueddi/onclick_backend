package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code quota_change_logs} (généré par scripts/scaffold-jpa.mjs).
 */
public record QuotaChangeLogDto(
    UUID id,
    UUID restaurantId,
    String serviceType,
    String serviceName,
    Integer oldQuota,
    Integer newQuota,
    UUID changedBy,
    String changedByName,
    String changeSource,
    Instant createdAt
) {
}
