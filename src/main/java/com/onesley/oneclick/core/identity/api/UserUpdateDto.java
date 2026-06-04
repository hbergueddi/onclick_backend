package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language
) {
}
