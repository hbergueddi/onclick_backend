package com.onesley.oneclick.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO d'écriture (PATCH) pour {@code profiles}.
 *
 * <p>Convention : tous les champs nullables — un {@code null} = "ne pas modifier".
 * Le service applique {@code if (dto.x() != null) entity.setX(dto.x())}.
 *
 * <p>L'ID, l'email, la {@code tenantId}, le {@code referralCode}, les timestamps
 * et le {@code reliabilityScore} sont volontairement absents — ils sont gérés
 * par d'autres flux (signup, parrainage, audit, calcul de score métier).
 */
public record ProfileUpdateDto(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "phone format invalid") String phone,
    @Size(max = 100) String city,
    String avatarUrl,
    String communityCoverUrl,
    List<String> allergens,
    @Pattern(regexp = "^(fr|en|ar)$", message = "language must be fr/en/ar") String language
) {
}
