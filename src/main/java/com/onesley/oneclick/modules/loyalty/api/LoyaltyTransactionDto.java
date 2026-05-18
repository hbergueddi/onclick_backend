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
 *
 * <h3>Champs enrichis {@code clientId} + {@code restaurantId}</h3>
 * <p>Ajoutés Bug 28 (PulsePro Dashboard Client) — la table {@code loyalty_transactions}
 * stocke uniquement {@code account_id} (FK vers {@code loyalty_accounts}). Les pages
 * d'agrégation par client (PulsePro, ClientSummary group view) ont besoin du
 * {@code client_id} pour éviter un JOIN N+1 côté frontend.</p>
 *
 * <p>Les deux champs sont nullable : remplis uniquement par les repo methods
 * qui utilisent une projection JPQL enrichie via {@code JOIN LoyaltyAccount}
 * (cf. {@link com.onesley.oneclick.modules.loyalty.internal.LoyaltyTransactionRepository#findAllByRestaurantIdEnriched}).
 * Pour le chemin standard {@code LoyaltyTransaction.toDto()} (single-row read),
 * ils restent à {@code null} — l'appelant peut les ignorer s'il n'en a pas besoin.</p>
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
    UUID createdById,
    /** Client propriétaire du compte (enrichi via JOIN — null sur chemin standard). */
    UUID clientId,
    /** Restaurant du compte (enrichi via JOIN — null sur chemin standard). */
    UUID restaurantId
) {
    /**
     * Constructeur legacy (pré-Bug 28) pour le chemin standard
     * {@code LoyaltyTransaction.toDto()} qui n'a pas accès au {@link LoyaltyAccount}.
     * Délègue au record canonique avec {@code clientId} et {@code restaurantId} à {@code null}.
     */
    public LoyaltyTransactionDto(
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
        this(id, accountId, type, points, amount, reason, expiresAt, createdAt, createdById, null, null);
    }
}
