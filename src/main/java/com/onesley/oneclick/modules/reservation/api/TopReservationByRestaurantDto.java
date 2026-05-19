package com.onesley.oneclick.modules.reservation.api;

import java.util.UUID;

/**
 * DTO d'agrégat : nombre de réservations par restaurant sur une période.
 *
 * <p>Sortie de {@code GET /api/reservations/top-by-restaurant?sinceDays=...&status=...}.
 * Consommé par la widget "Top Réservations · Par Ville" / "Par Restaurant" de
 * l'admin Restaurants — le frontend re-agrège côté client par dimension
 * (ville ou nom de restaurant) à partir de cette liste plate.
 *
 * <p>Anti-N+1 : remplace l'ancien comportement "fetch toutes les résa puis
 * compter" qui ne passait pas à l'échelle (3500+ résa en DB).
 */
public record TopReservationByRestaurantDto(
    UUID restaurantId,
    long reservationCount
) {
}
