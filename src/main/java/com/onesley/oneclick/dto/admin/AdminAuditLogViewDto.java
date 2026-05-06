package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_admin_audit_log} (généré par scripts/scaffold-jpa.mjs).
 */
public record AdminAuditLogViewDto(
    UUID id,
    Instant createdAt,
    UUID actorId,
    String actorEmail,
    String actorName,
    String action,
    String entityType,
    UUID entityId,
    String entityLabel,
    Map<String, Object> diff,
    Map<String, Object> metadata
) {
}
