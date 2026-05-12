package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO public d'un palier de fidélité (Ruby, Sapphire, Émeraude, Black…).
 *
 * <p>Schéma OneClick : pas de colonne {@code color} en DB, le rendu visuel est
 * géré côté front via mapping par {@code name}. {@code bonusPercent} est le bonus
 * de points appliqué pour les membres du palier (ex: +5% earn). {@code sortOrder}
 * permet l'ordre d'affichage croissant Ruby → Black.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait
 * via {@code Tier.toDto()} (dépendance internal → api autorisée en Modulith CLOSED).</p>
 */
public record TierDto(
    UUID id,
    UUID tenantId,
    String name,
    Integer minPoints,
    BigDecimal bonusPercent,
    Integer sortOrder
) {
}
