package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code monitor_logs} (généré par scripts/scaffold-jpa.mjs).
 */
public record MonitorLogDto(
    UUID id,
    Instant createdAt,
    String source,
    String eventType,
    String status,
    UUID userId,
    String platform,
    Map<String, Object> metadata,
    String errorMessage,
    Integer durationMs,
    String ipAddress
) {
}
