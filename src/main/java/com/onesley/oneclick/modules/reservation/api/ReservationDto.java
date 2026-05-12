package com.onesley.oneclick.modules.reservation.api;

import java.time.Instant;
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
    // ─── Joins frontend (anti N+1) — null pour les lectures unitaires ──────
    String clientFirstName,
    String clientLastName,
    String restaurantName,
    String restaurantCity,
    String restaurantImage,
    String mealServiceName,
    String zoneName,
    String tableNumber,
    String refusalReason,
    String cancellationReason
) {

    /** Constructeur "léger" — utilisé par {@code Reservation.toDto()} pour les écritures (champs joints null). */
    public ReservationDto(
        UUID id, UUID tenantId, UUID clientId, UUID restaurantId, UUID tableId, UUID serviceId,
        Instant reservationAt, Integer guestCount, String status, String notes, Instant createdAt
    ) {
        this(id, tenantId, clientId, restaurantId, tableId, serviceId,
            reservationAt, guestCount, status, notes, createdAt,
            null, null, null, null, null, null, null, null, null, null);
    }
}
