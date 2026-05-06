package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code admin_audit_log} (généré par scripts/scaffold-jpa.mjs).
 */
public record AdminAuditLogDto(
    UUID id,
    UUID actorId,
    String actorEmail,
    String action,
    String entityType,
    UUID entityId,
    String entityLabel,
    Map<String, Object> diff,
    Map<String, Object> metadata,
    String ip,
    String userAgent,
    Instant createdAt,
    UUID tenantId
) {
}
