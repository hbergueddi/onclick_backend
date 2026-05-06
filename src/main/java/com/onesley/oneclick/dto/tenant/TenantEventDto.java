package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code tenant_events} (généré par scripts/scaffold-jpa.mjs).
 */
public record TenantEventDto(
    UUID id,
    UUID tenantId,
    String title,
    String description,
    String photoUrl,
    String category,
    Instant eventDate,
    Instant eventEndDate,
    Integer capacity,
    Boolean rsvpEnabled,
    String status,
    Integer displayOrder,
    Instant createdAt,
    Instant updatedAt,
    Instant visibleUntil
) {
}
