package com.onesley.oneclick.modules.promotion.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un état « lu » d'une offre par un utilisateur (table
 * {@code offer_reads}, V63).
 *
 * <p>Retourné par {@code POST /api/offers/{id}/read} (marquage / re-marquage
 * idempotent). {@code userId} est toujours l'utilisateur courant (ABAC service)
 * — jamais un id arbitraire.</p>
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code OfferReadService} dans le package {@code internal} (l'entité reste
 * volontairement sans {@code toDto()}, comme un simple log d'état).</p>
 */
public record OfferReadDto(
    UUID offerId,
    UUID userId,
    Instant readAt,
    Instant createdAt
) {
}
