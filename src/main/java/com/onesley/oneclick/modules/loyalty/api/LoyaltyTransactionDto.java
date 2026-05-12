package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une transaction fidélité.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait
 * via {@code LoyaltyTransaction.toDto()} (dépendance internal → api autorisée
 * en Modulith CLOSED).</p>
 */
public record LoyaltyTransactionDto(
    UUID id,
    UUID accountId,
    String type,
    Integer points,
    BigDecimal amount,
    String reason,
    Instant expiresAt,
    Instant createdAt,
    UUID createdById
) {
}
