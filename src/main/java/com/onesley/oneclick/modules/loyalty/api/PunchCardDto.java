package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une carte de fidélité « punch card » 10/1 (PCC Lot 3).
 *
 * <p>{@code remaining} = punches restant avant la prochaine séance gratuite dans le
 * palier courant, soit {@code threshold - (countPunched - redeemedCount*threshold)}.
 * Le {@code tenantId} et le {@code clientId} ne sont volontairement PAS exposés
 * (le membre ne voit que SES cartes ; pas de besoin de les re-révéler).
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code PunchCard.toDto()} (dépendance internal → api autorisée en Modulith CLOSED).</p>
 */
public record PunchCardDto(
    UUID id,
    String activity,
    int countPunched,
    int threshold,
    int redeemedCount,
    int remaining,
    Instant lastPunchedAt
) {
}
