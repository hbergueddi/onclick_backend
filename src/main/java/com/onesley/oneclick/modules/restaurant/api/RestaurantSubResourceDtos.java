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
        Instant createdAt,
        // V51 — quotas Click&Go
        String type,
        Integer clickgoQuota,
        Integer capaciteMax,
        String status
    ) {}

    public record MealServiceCreateDto(
        @NotBlank @Size(min = 1, max = 128) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 32) String type,
        @Min(0) Integer clickgoQuota,
        @Min(0) Integer capaciteMax,
        @Size(max = 16) String status
    ) {}

    public record MealServicePatchDto(
        @Size(min = 1, max = 128) String name,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 32) String type,
        @Min(0) Integer clickgoQuota,
        @Min(0) Integer capaciteMax,
        @Size(max = 16) String status
    ) {}

    /**
     * Vue admin consolidée d'un créneau de service, enrichie du restaurant + groupe.
     * Alimente l'écran /reservations · onglet "Quotas Click&Go" (tous restaurants).
     * Projection native — cf {@code MealServiceRepository.findAllOverview}.
     */
    public record MealServiceOverviewDto(
        UUID id,
        UUID restaurantId,
        String restaurantName,
        String restaurantCity,
        String groupName,
        String type,
        String name,
        Integer clickgoQuota,
        Integer capaciteMax,
        String status,
        LocalTime startTime,
        LocalTime endTime
    ) {}

    // ─── RestaurantZone (Terrasse, Salle, Bar) ───────────────────────────────

    public record RestaurantZoneDto(
        UUID id,
        UUID restaurantId,
        String name,
        String type,
        String description,
        Integer capacity,
        String status,
        Instant createdAt
    ) {}

    public record RestaurantZoneCreateDto(
        @NotBlank @Size(min = 1, max = 128) String name,
        @Size(max = 64) String type,
        @Size(max = 512) String description,
        @Min(0) Integer capacity,
        @Size(max = 64) String status
    ) {}

    /** Patch partiel d'une zone (V50) — seuls les champs non-null sont appliqués. */
    public record RestaurantZonePatchDto(
        @Size(min = 1, max = 128) String name,
        @Size(max = 64) String type,
        @Size(max = 512) String description,
        @Min(0) Integer capacity,
        @Size(max = 64) String status
    ) {}

    // ─── RestaurantTable (T01, T02, ... rattachées à une zone) ───────────────

    public record RestaurantTableDto(
        UUID id,
        UUID zoneId,
        String tableNumber,
        Integer seats,
        String shape,
        String position,
        String status,
        Instant createdAt
    ) {}

    public record RestaurantTableCreateDto(
        @NotNull UUID zoneId,
        @NotBlank @Size(min = 1, max = 64) String tableNumber,
        @NotNull @Min(1) Integer seats,
        @Size(max = 64) String shape,
        @Size(max = 128) String position,
        @Size(max = 64) String status
    ) {}

    /** Patch partiel d'une table (V50) — seuls les champs non-null sont appliqués.
     * {@code zoneId} permet de déplacer la table vers une autre zone du même restaurant. */
    public record RestaurantTablePatchDto(
        UUID zoneId,
        @Size(min = 1, max = 64) String tableNumber,
        @Min(1) Integer seats,
        @Size(max = 64) String shape,
        @Size(max = 128) String position,
        @Size(max = 64) String status
    ) {}

    // ─── Annonce éphémère 24h (Gap #6 — port legacy 14/05) ────────────────────

    /** Annonce active d'un restaurant (message + expiration). */
    public record RestaurantAnnouncementDto(
        UUID id,
        UUID restaurantId,
        String message,
        UUID authorId,
        Instant createdAt,
        Instant expiresAt
    ) {}

    /** Corps de publication d'une annonce (message ≤ 280 ; expiration forcée +24h serveur). */
    public record RestaurantAnnouncementCreateDto(
        @NotBlank @Size(min = 1, max = 280) String message
    ) {}
}
