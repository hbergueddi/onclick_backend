package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la file plateforme des tickets scannés (Snap2Earn) — page TrustWatch
 * « File de tickets ». Un ticket scanné = transaction loyalty type {@code earn}
 * dont le {@code reason} encode le scan ({@code snap2earn|<ticketRef>|<photo?>}).
 *
 * <p>Pas de statut de validation côté Spring : un ticket scanné est crédité
 * immédiatement (« validé »). {@code restaurantId} est résolu via le compte
 * loyalty ; le nom du restaurant est résolu côté front (catalogue déjà chargé).
 */
public record ScannedTicketDto(
    UUID id,
    String ticketRef,
    UUID restaurantId,
    BigDecimal amount,
    Integer points,
    Instant createdAt
) {
}
