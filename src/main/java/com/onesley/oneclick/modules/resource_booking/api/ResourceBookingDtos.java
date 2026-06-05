package com.onesley.oneclick.modules.resource_booking.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module resource_booking.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class ResourceBookingDtos {

    private ResourceBookingDtos() {}

    // ─── Resource ────────────────────────────────────────────────────────────

    public record ResourceDto(UUID id, UUID tenantId, String resourceType, String name, String description,
                              Integer capacity, boolean enabled, Instant createdAt) {}

    public record ResourceCreateDto(
        @NotNull UUID tenantId,
        @NotBlank @Size(min = 1, max = 64) String resourceType,
        @NotBlank @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        @PositiveOrZero Integer capacity
    ) {}

    // ─── Pricing ─────────────────────────────────────────────────────────────

    public record PricingDto(UUID id, UUID resourceId, String name, BigDecimal price, Integer durationMinutes,
                             boolean enabled, Instant createdAt) {}

    public record PricingCreateDto(
        @NotNull UUID resourceId,
        @NotBlank @Size(min = 1, max = 128) String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @PositiveOrZero Integer durationMinutes
    ) {}

    // ─── Booking ─────────────────────────────────────────────────────────────

    public record BookingDto(UUID id, UUID resourceId, UUID organizerId, UUID pricingId, Instant startAt,
                             Instant endAt, String status, String notes, Instant createdAt) {}

    /**
     * Booking enrichi pour le <b>dashboard staff</b> ({@code GET /bookings?scope=tenant}).
     *
     * <p>Le board opérationnel staff doit afficher QUI a réservé QUELLE ressource — contrairement
     * au {@link BusySlotDto} (calendrier membre, zéro PII). On expose donc le <b>nom d'affichage</b>
     * de l'organisateur ({@code organizerName}, prénom + nom via {@code UserDirectoryApi}) et le
     * <b>nom de la ressource</b> ({@code resourceName}), mais <b>jamais</b> le téléphone/email de
     * l'organisateur (PII minimisée : un dashboard de planning n'en a pas besoin).</p>
     *
     * <p>Réservé au staff/admin du tenant (ABAC service : un CLIENT qui demande {@code scope=tenant}
     * est refusé 403). Les noms sont résolus en un seul batch (anti-N+1).</p>
     */
    public record StaffBookingDto(UUID id, UUID resourceId, String resourceName, UUID organizerId,
                                  String organizerName, UUID pricingId, Instant startAt, Instant endAt,
                                  String status, String notes, Instant createdAt) {}

    public record BookingCreateDto(
        @NotNull UUID resourceId,
        @NotNull UUID organizerId,
        UUID pricingId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Pattern(regexp = "^(pending|confirmed|cancelled|no_show|completed)$") @Size(min = 1, max = 64) String status,
        @Size(min = 1, max = 1024) String notes
    ) {}

    public record BookingUpdateDto(
        @Pattern(regexp = "^(pending|confirmed|cancelled|no_show|completed)$") @Size(min = 1, max = 64) String status,
        @Size(min = 1, max = 1024) String notes
    ) {}

    // ─── Busy slot (disponibilité calendrier, sans PII) ───────────────────────

    /**
     * Créneau occupé d'une ressource — exposé au calendrier de réservation membre.
     *
     * <p><b>Volontairement sans PII</b> : ni organisateur, ni invités, ni notes, ni statut.
     * Le membre voit uniquement QU'un créneau est pris, jamais QUI l'a réservé. Conçu pour
     * {@code GET /resources/{id}/busy-slots?date=YYYY-MM-DD}.</p>
     */
    public record BusySlotDto(Instant startAt, Instant endAt) {}

    // ─── Guest ───────────────────────────────────────────────────────────────

    public record GuestDto(UUID id, UUID bookingId, UUID guestUserId, String guestName, Instant createdAt) {}

    public record GuestCreateDto(
        @NotNull UUID bookingId,
        UUID guestUserId,
        @Size(min = 1, max = 128) String guestName
    ) {}
}
