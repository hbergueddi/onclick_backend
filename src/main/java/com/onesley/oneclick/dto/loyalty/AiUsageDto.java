package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code ai_usage} (généré par scripts/scaffold-jpa.mjs).
 */
public record AiUsageDto(
    UUID id,
    UUID userId,
    Integer promptCount,
    Instant lastPromptAt,
    Instant createdAt
) {
}
