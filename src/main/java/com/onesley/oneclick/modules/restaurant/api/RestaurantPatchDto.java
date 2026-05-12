package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

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
 * <p>{@code status} validé via regex : {@code actif | inactif | suspendu | archive}.
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
    @Pattern(regexp = "^(actif|inactif|suspendu|archive)$") String status,
    @Pattern(regexp = "^(€|€€|€€€)$") String budget,
    List<String> tags,
    Integer loungePts,
    String image
) {
}
