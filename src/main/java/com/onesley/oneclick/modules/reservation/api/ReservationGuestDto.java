package com.onesley.oneclick.modules.reservation.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics pour le workflow {@link com.onesley.oneclick.modules.reservation.internal.ReservationGuest}.
 *
 * <p>Sprint G.2.1 — port du workflow legacy {@code reservation_guests} avec
 * {@code guest_phone}, {@code invited_by}, {@code status}, {@code seen_by_host}
 * (cf migration V17).
 */
public record ReservationGuestDto(
    UUID id,
    UUID reservationId,
    UUID guestUserId,
    String guestName,
    String guestPhone,
    UUID invitedById,
    String status,
    boolean seenByHost,
    Instant createdAt
) {

    /**
     * Payload pour {@code POST /api/reservations/{id}/guests} — invite un guest.
     *
     * <p>Au moins un des 3 identifiants doit être fourni :
     * <ul>
     *   <li>{@code guestUserId} : user OneClick existant</li>
     *   <li>{@code guestPhone} : téléphone (pas encore user)</li>
     *   <li>{@code guestName} : nom libre (placeholder)</li>
     * </ul>
     */
    public record CreateDto(
        UUID guestUserId,
        String guestName,
        String guestPhone,
        UUID invitedBy,
        @Pattern(regexp = "^(linked|invited|accepted|refused|cancelled)$") String status
    ) {}

    /**
     * Payload pour {@code PATCH /api/reservations/guests/{id}} — change le statut
     * (typique : guest répond à l'invitation → accepted/refused).
     */
    public record StatusUpdateDto(
        @NotNull @Pattern(regexp = "^(linked|invited|accepted|refused|cancelled)$") String status
    ) {}
}
