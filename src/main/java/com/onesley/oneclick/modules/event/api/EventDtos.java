package com.onesley.oneclick.modules.event.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
        @NotBlank @Size(min = 1, max = 128) String title,
        @Size(min = 1, max = 1024) String description,
        @Size(min = 1, max = 64) String eventType,
        @NotNull Instant eventAt,
        Instant eventEnd,
        @PositiveOrZero Integer capacity,
        // V20 — Elite fields (optionnels)
        @Pattern(regexp = "^(Ruby|Sapphire|Émeraude|Black)$") @Size(min = 1, max = 128) String minTier,
        @Size(min = 1, max = 512) String imageUrl,
        @Size(min = 1, max = 128) String locationName,
        Boolean isActive
    ) {}

    public record EventPatchDto(
        @Size(min = 1, max = 128) String title,
        @Size(min = 1, max = 1024) String description,
        @Size(min = 1, max = 64) String eventType,
        Instant eventAt,
        Instant eventEnd,
        @PositiveOrZero Integer capacity,
        @Pattern(regexp = "^(Ruby|Sapphire|Émeraude|Black)$") @Size(min = 1, max = 128) String minTier,
        @Size(min = 1, max = 512) String imageUrl,
        @Size(min = 1, max = 128) String locationName,
        Boolean isActive
    ) {}

    /**
     * @param memberFirstName / memberLastName / memberEmail  identité du membre
     *        (jointure {@code user}) — affichée dans la liste des inscrits admin.
     */
    public record ParticipationDto(
        UUID id, UUID eventId, UUID userId,
        String memberFirstName, String memberLastName, String memberEmail,
        String status, String plusOneName, Instant createdAt
    ) {}

    public record ParticipationCreateDto(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @Pattern(regexp = "^(going|maybe|declined|attended)$") @Size(min = 1, max = 64) String status,
        @Size(min = 1, max = 128) String plusOneName
    ) {}
}
