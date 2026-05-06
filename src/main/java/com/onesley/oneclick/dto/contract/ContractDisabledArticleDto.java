package com.onesley.oneclick.dto.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code contract_disabled_articles} (généré par scripts/scaffold-jpa.mjs).
 */
public record ContractDisabledArticleDto(
    UUID id,
    UUID contractId,
    UUID articleId,
    UUID disabledBy,
    String reason,
    Instant createdAt
) {
}
