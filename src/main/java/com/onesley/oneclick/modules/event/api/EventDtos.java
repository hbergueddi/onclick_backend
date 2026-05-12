package com.onesley.oneclick.modules.event.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module event.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} (dépendance internal → api autorisée en Modulith
 * CLOSED ; {@code Event} étant exposé via api/, son {@code toDto()} reste
 * local au package).</p>
 */
public final class EventDtos {

    private EventDtos() {}

    public record EventDto(UUID id, UUID tenantId, UUID restaurantId, String title, String description,
                           String eventType, Instant eventAt, Integer capacity, Instant createdAt) {}

    public record EventCreateDto(
        @NotNull UUID tenantId,
        UUID restaurantId,
        @NotBlank String title,
        String description,
        String eventType,
        @NotNull Instant eventAt,
        Integer capacity
    ) {}

    public record ParticipationDto(UUID id, UUID eventId, UUID userId, String status, Instant createdAt) {}

    public record ParticipationCreateDto(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @Pattern(regexp = "^(going|maybe|declined|attended)$") String status
    ) {}
}
