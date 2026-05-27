package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTOs publics des sous-ressources du module restaurant : staff, services (repas),
 * zones et tables.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 *
 * <p>Pattern aligné sur {@link com.onesley.oneclick.modules.social.api.SocialDtos}
 * (un fichier groupé pour les DTOs sœurs d'un même module).</p>
 */
public final class RestaurantSubResourceDtos {

    private RestaurantSubResourceDtos() {}

    // ─── Staff (junction user × restaurant) ──────────────────────────────────

    /**
     * Membre du staff d'un restaurant. Les champs {@code user*} sont l'enrichissement
     * serveur-side du profil (résolus dans {@code RestaurantSubResourceService.listStaff}
     * via le domaine identity) — évite que le front appelle {@code /api/users/by-ids}
     * (VIEW:USERS, refusé au RESTAURATEUR/STAFF). {@code null} hors contexte de liste.
     */
    public record RestaurantStaffDto(
        UUID id,
        UUID restaurantId,
        UUID userId,
        String roleCode,
        Instant createdAt,
        String userFirstName,
        String userLastName,
        String userPhone
    ) {}

    public record RestaurantStaffCreateDto(
        @NotNull UUID userId,
        @NotBlank @Size(min = 1, max = 64) String roleCode
    ) {}

    /**
     * Patch partiel d'un staff.
     *
     * <ul>
     *   <li>{@code roleCode} — change le rôle applicatif (le user_id reste immuable).</li>
     *   <li>{@code active} — réactive ({@code true}) ou désactive ({@code false}) le
     *       staff via le soft-delete {@code deleted_at}. {@code null} = pas de changement.</li>
     * </ul>
     */
    public record RestaurantStaffPatchDto(
        @Size(min = 1, max = 64) String roleCode,
        Boolean active,
        // Sprint M — édition du profil du membre par l'owner (UPDATE:STAFF) : si
        // présents, on met à jour le user lié (évite PATCH /api/users/{id} = UPDATE:USERS).
        @Size(min = 1, max = 128) String firstName,
        @Size(min = 1, max = 128) String lastName,
        @Size(min = 1, max = 64) String phone
    ) {}

    // ─── MealService (créneau brunch/déjeuner/dîner) ─────────────────────────

    public record MealServiceDto(
        UUID id,
        UUID restaurantId,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        Instant createdAt
    ) {}

    public record MealServiceCreateDto(
        @NotBlank @Size(min = 1, max = 128) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
    ) {}

    public record MealServicePatchDto(
        @Size(min = 1, max = 128) String name,
        LocalTime startTime,
        LocalTime endTime
    ) {}

    // ─── RestaurantZone (Terrasse, Salle, Bar) ───────────────────────────────

    public record RestaurantZoneDto(
        UUID id,
        UUID restaurantId,
        String name,
        Instant createdAt
    ) {}

    public record RestaurantZoneCreateDto(
        @NotBlank @Size(min = 1, max = 128) String name
    ) {}

    // ─── RestaurantTable (T01, T02, ... rattachées à une zone) ───────────────

    public record RestaurantTableDto(
        UUID id,
        UUID zoneId,
        String tableNumber,
        Integer seats,
        Instant createdAt
    ) {}

    public record RestaurantTableCreateDto(
        @NotNull UUID zoneId,
        @NotBlank @Size(min = 1, max = 64) String tableNumber,
        @NotNull @Min(1) Integer seats
    ) {}
}
