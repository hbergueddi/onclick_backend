package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code ai_usage_bypass} (généré par scripts/scaffold-jpa.mjs).
 */
public record AiUsageBypassDto(
    UUID userId,
    String reason,
    Instant createdAt
) {
}
