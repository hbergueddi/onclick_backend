package com.onesley.oneclick.dto.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code contract_templates} (généré par scripts/scaffold-jpa.mjs).
 */
public record ContractTemplateDto(
    UUID id,
    String name,
    String version,
    String preamble,
    String footer,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    UUID tenantId
) {
}
