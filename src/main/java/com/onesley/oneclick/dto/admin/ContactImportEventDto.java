package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code contact_import_events} (généré par scripts/scaffold-jpa.mjs).
 */
public record ContactImportEventDto(
    UUID id,
    UUID userId,
    Instant importedAt,
    Integer phoneCount,
    Integer matchedCount
) {
}
