package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code email_bounces} (généré par scripts/scaffold-jpa.mjs).
 */
public record EmailBounceDto(
    UUID id,
    String email,
    String bounceType,
    String bounceReason,
    Boolean isSuppressed,
    Instant lastBouncedAt,
    Integer bounceCount,
    String sourceEf,
    Map<String, Object> rawEvent,
    Instant createdAt,
    Instant updatedAt
) {
}
