package com.onesley.oneclick.modules.reservation.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection Spring Data — contexte d'affichage d'une réservation contestée : nom du
 * restaurant + horodatage de la résa, joints par read-view native.
 *
 * <h3>Modulith — pourquoi une interface + nativeQuery ?</h3>
 * <p>La page admin/support de litiges affiche le NOM du restaurant (pas son UUID). Ce nom vit
 * dans la table {@code restaurants} ({@code modules.restaurant.internal}) — non importable
 * depuis {@code modules.reservation} (CLOSED, 0 dépendance business↔business). On reste donc au
 * niveau SQL (noms de tables) via une {@code nativeQuery} dans
 * {@link NoShowDisputeRepository#findReservationContextByIds} ; le binding est assuré par cette
 * projection (getters {@code get<ColumnAlias>}). Même pattern que {@code PccFeedbackOwnerView}
 * (P2.c).</p>
 *
 * <p>{@code reservationId} sert de clé pour le regroupement anti-N+1 côté service (une seule
 * requête pour tout un lot de disputes → {@code Map<reservationId, view>}).</p>
 */
public interface DisputeReservationView {
    UUID getReservationId();
    String getRestaurantName();
    Instant getReservationDateTime();
}
