package com.onesley.oneclick.dto.tenant;

import com.onesley.oneclick.entity.shared.AnnouncementPriority;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code tenant_announcements} (généré par scripts/scaffold-jpa.mjs).
 */
public record TenantAnnouncementDto(
    UUID id,
    UUID tenantId,
    UUID authorId,
    String title,
    String body,
    String imageUrl,
    AnnouncementPriority priority,
    Boolean isPinned,
    Instant publishAt,
    Instant archivedAt,
    Instant deletedAt,
    Integer bodyVersion,
    Instant pushSentAt,
    Instant createdAt,
    Instant updatedAt,
    List<UUID> targetRestaurantIds
) {
}
