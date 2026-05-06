package com.onesley.oneclick.dto.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code contract_template_articles} (généré par scripts/scaffold-jpa.mjs).
 */
public record ContractTemplateArticleDto(
    UUID id,
    UUID templateId,
    Integer articleNumber,
    String title,
    String content,
    Integer sortOrder,
    Instant createdAt
) {
}
