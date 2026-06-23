package com.onesley.oneclick.modules.reservation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO public d'une réservation restaurant.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Reservation.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 *
 * <h3>Anti N+1 — champs joints</h3>
 * <p>Les champs sous le séparateur "join" sont remplis uniquement par les
 * lectures groupées ({@code findAll}, {@code search}) qui passent par une
 * requête native avec {@code LEFT JOIN} sur {@code users}, {@code restaurants},
 * {@code restaurant_tables}, {@code restaurant_zones}, {@code restaurant_services}
 * et {@code reservation_status_histories}. Pour les autres usages
 * ({@code create}, {@code changeStatus}, {@code findById}) ces champs sont
 * {@code null} — le client peut récupérer les références via les endpoints
 * dédiés s'il en a besoin.</p>
 *
 * <p>Ce design évite que le front (Pocket Compass / Vault / MyReservations)
 * ne déclenche un {@code GET /api/users/{clientId}} + {@code GET
 * /api/restaurants/{restaurantId}} par ligne de liste.</p>
 */
public record ReservationDto(
    UUID id,
    UUID tenantId,
    UUID clientId,
    UUID restaurantId,
    UUID tableId,
    UUID serviceId,
    Instant reservationAt,
    Integer guestCount,
    String status,
    String notes,
    Instant createdAt,
    // ─── Feature #4 — flag annulation tardive (toujours rempli) ───────────
    boolean lateCancellation,
    // ─── Feature #3/#4 — horodatage du passage en no_show (null sinon) ────
    Instant noShowMarkedAt,
    // ─── Contre-proposition — nouveau créneau proposé par le resto (null hors counter_proposed) ──
    Instant proposedReservationAt,
    // ─── Joins frontend (anti N+1) — null pour les lectures unitaires ──────
    String clientFirstName,
    String clientLastName,
    String clientPhone,
    // ─── Visibilité staff (V65) — allergènes du client sur la fiche réservation ──
    // Rempli uniquement par les lectures enrichies (findAll / by-restaurants) ;
    // null/[] pour les lectures unitaires (Reservation.toDto). La résa est déjà
    // scopée (VIEW:RESERVATIONS + ABAC) → pas de nouvelle autorité.
    List<String> clientAllergens,
    String restaurantName,
    String restaurantCity,
    String restaurantImage,
    // ─── Géo restaurant (Maps « Y aller » sur la fiche réservation, invité inclus) ──
    // Joints, remplis uniquement par les lectures enrichies ; null pour les écritures.
    String restaurantAddress,
    BigDecimal restaurantLatitude,
    BigDecimal restaurantLongitude,
    String restaurantGooglePlaceId,
    String mealServiceName,
    String zoneName,
    String tableNumber,
    String refusalReason,
    String cancellationReason
) {

    /** Constructeur "léger" — utilisé par {@code Reservation.toDto()} pour les écritures (champs joints null). */
    public ReservationDto(
        UUID id, UUID tenantId, UUID clientId, UUID restaurantId, UUID tableId, UUID serviceId,
        Instant reservationAt, Integer guestCount, String status, String notes, Instant createdAt,
        boolean lateCancellation, Instant noShowMarkedAt, Instant proposedReservationAt
    ) {
        this(id, tenantId, clientId, restaurantId, tableId, serviceId,
            reservationAt, guestCount, status, notes, createdAt, lateCancellation, noShowMarkedAt,
            proposedReservationAt,
            // joints (clientFirstName, clientLastName, clientPhone, clientAllergens,
            // restaurantName, restaurantCity, restaurantImage, restaurantAddress,
            // restaurantLatitude, restaurantLongitude, restaurantGooglePlaceId,
            // mealServiceName, zoneName, tableNumber, refusalReason, cancellationReason) → null écritures.
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
