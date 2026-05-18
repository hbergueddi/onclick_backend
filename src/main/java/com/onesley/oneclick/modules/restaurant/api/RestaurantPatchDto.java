package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Patch partiel d'un Restaurant — Sprint G.2.2 (PATCH /api/restaurants/{id}).
 *
 * <p>Sémantique PATCH : tous les champs optionnels. Les champs {@code null} ne
 * sont pas modifiés. Pour effacer une valeur (ex: vider le téléphone), passer
 * une chaîne vide.
 *
 * <p>RBAC côté controller : owner du restaurant (staff_role=owner) ou
 * SUPERADMIN/GROUP_ADMIN.
 *
 * <p>{@code status} validé via regex : {@code active | paused | archived}.
 * {@code budget} : {@code €} | {@code €€} | {@code €€€} (V16).
 */
public record RestaurantPatchDto(
    String name,
    String description,
    String phone,
    String address,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    // Bug 30 — Aligné sur la canonique DB EN du Restaurant entity
    // (@Pattern("^(active|paused|archived)$") ligne 65). Le legacy Supabase
    // utilisait 'actif|inactif|suspendu|archive' (FR) — toute PATCH avec ces
    // valeurs passait la validation DTO puis échouait silencieusement sur
    // l'entity Hibernate (500). Le frontend doit envoyer la valeur EN
    // canonique (cf translateStatusToBackend côté RestaurantActionsMenu).
    @Pattern(regexp = "^(active|paused|archived)$") String status,
    @Pattern(regexp = "^(€|€€|€€€)$") String budget,
    List<String> tags,
    Integer loungePts,
    String image,
    String cuisine,
    @Min(0) Integer maxStaff,
    UUID groupId
) {
}
