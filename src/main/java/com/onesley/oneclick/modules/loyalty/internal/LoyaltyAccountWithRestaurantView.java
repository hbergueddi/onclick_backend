package com.onesley.oneclick.modules.loyalty.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection Spring Data — compte fidélité enrichi du nom/cuisine de son restaurant.
 *
 * <h3>Modulith — pourquoi une interface ?</h3>
 * <p>L'entité {@code Restaurant} vit dans {@code modules.restaurant.internal} et n'est
 * pas importable depuis {@code modules.loyalty} (CLOSED). On reste au niveau SQL (noms
 * de tables) via une {@code nativeQuery} dans
 * {@link LoyaltyAccountRepository#findAllByClientIdWithRestaurant} ; le binding est
 * assuré par cette projection (getters {@code get<ColumnAlias>}, alias snake_case →
 * camelCase automatique). Même pattern que {@code ReservationWithJoinsView}.</p>
 */
public interface LoyaltyAccountWithRestaurantView {
    UUID getId();
    UUID getClientId();
    UUID getRestaurantId();
    UUID getTierId();
    Integer getBalance();
    Instant getCreatedAt();
    // ─── Join restaurants (anti-N+1) ────────────────────────────────────────
    String getRestaurantName();
    String getRestaurantCuisine();
}
