package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code device_tokens} (généré par scripts/scaffold-jpa.mjs).
 */
public record DeviceTokenDto(
    UUID id,
    UUID userId,
    String token,
    String platform,
    Instant createdAt,
    Instant updatedAt,
    String appId
) {
}
