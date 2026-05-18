package com.onesley.oneclick.modules.reservation.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection Spring Data — lecture d'une réservation enrichie de ses joints
 * éditoriaux (utilisateur, restaurant, table, zone, service, raisons workflow).
 *
 * <h3>Modulith — pourquoi une interface ?</h3>
 * <p>Les Entity {@code Restaurant}, {@code RestaurantTable}, {@code RestaurantZone}
 * et {@code MealService} vivent dans {@code modules.restaurant.internal} et ne
 * sont pas importables depuis {@code modules.reservation} en Modulith CLOSED.
 * On reste au niveau SQL (table names) via une {@code nativeQuery} dans
 * {@link ReservationRepository#findAllWithJoins} ; le mapping est assuré par
 * cette projection à base d'interface Spring Data.</p>
 *
 * <p>Les getters sont nommés en {@code get<ColumnAlias>} — Spring s'occupe du
 * binding par convention via les alias SQL (en {@code snake_case} converti
 * automatiquement vers {@code camelCase}).</p>
 */
public interface ReservationWithJoinsView {

    UUID getId();
    UUID getTenantId();
    UUID getClientId();
    UUID getRestaurantId();
    UUID getTableId();
    UUID getServiceId();
    Instant getReservationAt();
    Integer getGuestCount();
    String getStatus();
    String getNotes();
    Instant getCreatedAt();

    // ─── Joins ─────────────────────────────────────────────────────────────
    String getClientFirstName();
    String getClientLastName();
    String getClientPhone();
    String getRestaurantName();
    String getRestaurantCity();
    String getRestaurantImage();
    String getMealServiceName();
    String getZoneName();
    String getTableNumber();
    String getRefusalReason();
    String getCancellationReason();
}
