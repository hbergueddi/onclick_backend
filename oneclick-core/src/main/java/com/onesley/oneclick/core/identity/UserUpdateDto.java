package com.onesley.oneclick.core.identity;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de mise à jour partielle pour PATCH /api/users/{id} — tous les champs nullable.
 * Champs sensibles (email, password, role) gérés par endpoints dédiés.
 */
public record UserUpdateDto(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") String phone,
    String avatarUrl,
    @Pattern(regexp = "^(fr|en|ar)$") String language
) {
}
