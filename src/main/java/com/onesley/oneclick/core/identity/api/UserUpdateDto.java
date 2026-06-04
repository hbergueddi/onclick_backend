package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO de mise à jour partielle pour PATCH /api/users/{id} — tous les champs nullable.
 * Champs sensibles (email, password, role) gérés par endpoints dédiés.
 */
public record UserUpdateDto(
    @Size(max = 100) @Size(min = 1, max = 128) String firstName,
    @Size(max = 100) @Size(min = 1, max = 128) String lastName,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") @Size(min = 1, max = 64) String phone,
    @Size(min = 1, max = 512) String avatarUrl,
    @Size(min = 1, max = 128) String city,
    @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language,
    // V65 — allergènes du profil client (parité legacy). On reste permissif sur les
    // slugs (le front envoie des codes EU connus, mais des customs sont tolérés comme
    // dans le legacy) ; on borne juste la taille de la liste pour éviter les abus.
    @Size(max = 20) List<String> allergens
) {
}
