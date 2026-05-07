package com.onesley.oneclick.dto.support;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code rule_templates} (généré par scripts/scaffold-jpa.mjs).
 */
public record RuleTemplateDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    String slug,
    String name,
    String description,
    String icon,
    String category,
    Map<String, Object> config,
    Boolean isBuiltin,
    Boolean enabled,
    UUID createdBy,
    UUID modifiedBy
) {
}
