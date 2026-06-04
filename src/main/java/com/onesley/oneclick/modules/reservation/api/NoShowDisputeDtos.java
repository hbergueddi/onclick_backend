package com.onesley.oneclick.modules.reservation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics pour le domaine contestation no-show (Feature #3) —
 * {@link com.onesley.oneclick.modules.reservation.internal.NoShowDispute}.
 *
 * <p>Records camelCase + Bean Validation. Le mapping Entity → {@link NoShowDisputeDto}
 * se fait via {@code NoShowDispute.toDto()} dans le package {@code internal}
 * (dépendance internal → api autorisée en Modulith CLOSED).
 */
public final class NoShowDisputeDtos {

    private NoShowDisputeDtos() {}

    /** DTO public d'une contestation de no_show. */
    public record NoShowDisputeDto(
        UUID id,
        UUID reservationId,
        UUID clientId,
        UUID restaurantId,
        String status,
        String escalationPhase,
        String reason,
        String photoUrl,
        String resolutionNote,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt
    ) {}

    /**
     * Payload pour {@code POST /api/reservations/{id}/disputes} — le client conteste.
     *
     * <p>{@code reason} obligatoire. {@code photoUrl} optionnel à la 1re contestation,
     * mais <b>obligatoire</b> lors d'une re-contestation après un refus (vérifié côté
     * service, car la règle dépend de l'état des disputes existantes).
     */
    public record CreateDisputeDto(
        @NotBlank @Size(min = 1, max = 2048) String reason,
        @Size(max = 2048) String photoUrl
    ) {}

    /**
     * Payload pour {@code PATCH /api/disputes/{id}} — résolution par resto/support/admin.
     *
     * <p>{@code status} ∈ {@code accepted | refused}. {@code resolutionNote} optionnel
     * (motif de la décision).
     */
    public record ResolveDisputeDto(
        @NotNull @Pattern(regexp = "^(accepted|refused)$") String status,
        @Size(max = 2048) String resolutionNote
    ) {}
}
