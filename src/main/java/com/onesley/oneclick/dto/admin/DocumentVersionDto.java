package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code document_versions} (généré par scripts/scaffold-jpa.mjs).
 */
public record DocumentVersionDto(
    UUID id,
    String documentId,
    String version,
    String content,
    Instant createdAt,
    UUID createdBy,
    String notes
) {
}
