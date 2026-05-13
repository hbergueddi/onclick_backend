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

    /** Sprint D — EventDto enrichi avec Elite fields (V20 schema). */
    public record EventDto(
        UUID id, UUID tenantId, UUID restaurantId, String title, String description,
        String eventType, Instant eventAt, Instant eventEnd,
        Integer capacity, Integer placesTaken,
        String minTier, String imageUrl, String locationName, boolean isActive,
        Instant createdAt
    ) {}

    public record EventCreateDto(
        @NotNull UUID tenantId,
        UUID restaurantId,
        @NotBlank String title,
        String description,
        String eventType,
        @NotNull Instant eventAt,
        Instant eventEnd,
        Integer capacity,
        // V20 — Elite fields (optionnels)
        @Pattern(regexp = "^(Ruby|Sapphire|Émeraude|Black)$") String minTier,
        String imageUrl,
        String locationName,
        Boolean isActive
    ) {}

    public record EventPatchDto(
        String title,
        String description,
        String eventType,
        Instant eventAt,
        Instant eventEnd,
        Integer capacity,
        @Pattern(regexp = "^(Ruby|Sapphire|Émeraude|Black)$") String minTier,
        String imageUrl,
        String locationName,
        Boolean isActive
    ) {}

    public record ParticipationDto(
        UUID id, UUID eventId, UUID userId, String status,
        String plusOneName, Instant createdAt
    ) {}

    public record ParticipationCreateDto(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @Pattern(regexp = "^(going|maybe|declined|attended)$") String status,
        String plusOneName
    ) {}
}
