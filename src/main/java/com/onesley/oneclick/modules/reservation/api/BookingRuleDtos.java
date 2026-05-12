package com.onesley.oneclick.modules.reservation.api;

import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics des règles de réservation ({@code booking_rules}).
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code BookingRule.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 *
 * <p>Une règle est attachée à un restaurant et contrôle :
 * <ul>
 *   <li>{@code maxGuest} — couverts max par réservation (default 12)</li>
 *   <li>{@code slotDuration} — durée d'un slot en minutes (default 90)</li>
 *   <li>{@code cancellationWindowHours} — fenêtre d'annulation gratuite (default 2h)</li>
 * </ul>
 */
public final class BookingRuleDtos {

    private BookingRuleDtos() {}

    public record BookingRuleDto(
        UUID id,
        UUID restaurantId,
        Integer maxGuest,
        Integer slotDuration,
        Integer cancellationWindowHours,
        Instant createdAt
    ) {}

    public record BookingRuleCreateDto(
        @Min(1) Integer maxGuest,
        @Min(15) Integer slotDuration,
        @Min(0) Integer cancellationWindowHours
    ) {}

    public record BookingRulePatchDto(
        @Min(1) Integer maxGuest,
        @Min(15) Integer slotDuration,
        @Min(0) Integer cancellationWindowHours
    ) {}
}
