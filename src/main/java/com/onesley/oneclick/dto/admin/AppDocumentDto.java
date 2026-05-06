package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code app_documents} (généré par scripts/scaffold-jpa.mjs).
 */
public record AppDocumentDto(
    String id,
    String content,
    Instant updatedAt,
    String version,
    UUID updatedBy
) {
}
