package com.onesley.oneclick.dto.pcc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO pour {@code seminar_requests} (généré par scripts/scaffold-jpa.mjs).
 */
public record SeminarRequestDto(
    UUID id,
    UUID tenantId,
    UUID organizerId,
    String companyName,
    String contactName,
    String contactEmail,
    String contactPhone,
    Integer expectedAttendees,
    LocalDate preferredDateStart,
    LocalDate preferredDateEnd,
    String needsText,
    String status,
    String notesInternal,
    Instant createdAt,
    Instant updatedAt
) {
}
