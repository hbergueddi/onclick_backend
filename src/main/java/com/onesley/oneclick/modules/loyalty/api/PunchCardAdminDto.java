package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue STAFF/admin d'une carte de fidélité punch card (Gap #3 — export CSV).
 *
 * <p>Contrairement à {@link PunchCardDto} (self-scope, sans identité client), expose
 * le nom + téléphone du membre (résolus via {@code UserDirectoryApi}, core.identity)
 * pour l'export {@code export_punch_cards_csv} legacy. Gating {@code VIEW:STAFF}.
 */
public record PunchCardAdminDto(
    UUID clientId,
    String clientName,
    String phone,
    String activity,
    int countPunched,
    int threshold,
    int redeemedCount,
    Instant lastPunchedAt
) {
}
