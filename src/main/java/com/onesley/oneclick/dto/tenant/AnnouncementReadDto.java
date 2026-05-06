package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code announcement_reads} (généré par scripts/scaffold-jpa.mjs).
 */
public record AnnouncementReadDto(
    UUID announcementId,
    UUID userId,
    Integer bodyVersionRead,
    Instant readAt
) {
}
