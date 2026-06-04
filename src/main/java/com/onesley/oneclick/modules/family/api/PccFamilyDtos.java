package com.onesley.oneclick.modules.family.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics « Ma Famille » (PCC Lot 5) exposés par {@code PccFamilyController}.
 *
 * <p>Records immutables + Bean Validation sur les payloads d'entrée. Pas de logique de
 * mapping ici : la conversion Entity/projection → DTO se fait dans {@code PccFamilyService}.</p>
 */
public final class PccFamilyDtos {

    private PccFamilyDtos() {}

    /**
     * Payload d'ajout d'un proche. {@code identifier} = email / code parrainage {@code OC-…} /
     * téléphone (auto-détecté côté service via {@code UserDirectoryApi.findByIdentifier}).
     * {@code relation} libre mais bornée (les valeurs UI : Conjoint·e / Enfant / Parent /
     * Frère·Sœur / Autre) — optionnelle.
     */
    public record AddFamilyMemberDto(
        @NotBlank @Size(min = 1, max = 256) String identifier,
        @Size(max = 64) String relation
    ) {}

    /**
     * Un proche de ma liste famille. {@code totalRemainingPoints} = somme des soldes de
     * fidélité du proche, restreinte aux restaurants du tenant courant (read-view native).
     * {@code status} = {@code "added"} (création) ou {@code "already_added"} (idempotent) —
     * renseigné sur la réponse d'ajout, {@code null} en liste.
     */
    public record FamilyMemberDto(
        UUID relationId,
        UUID memberId,
        String firstName,
        String lastName,
        String email,
        String avatarUrl,
        String relation,
        long totalRemainingPoints,
        Instant addedAt,
        String status
    ) {}

    /**
     * Une ligne d'historique de points fidélité d'un proche (read-view native, scope tenant).
     * {@code remainingPoints} = solde courant du compte (par restaurant).
     */
    public record PointsHistoryEntryDto(
        UUID id,
        UUID restaurantId,
        String restaurantName,
        Integer points,
        BigDecimal amountTtc,
        String reason,
        Instant earnedAt,
        Integer remainingPoints,
        Instant expiresAt
    ) {}
}
