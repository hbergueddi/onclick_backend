package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pour POST /api/users/{id}/password — changement de mot de passe.
 *
 * <p>RBAC strict : seul l'owner exact peut changer son password (admins refusés,
 * cf {@link com.onesley.oneclick.security.SecurityHelper#requireOwnerExact}).
 *
 * <p>Le service vérifie {@code currentPassword} via {@code passwordEncoder.matches},
 * puis ré-encode {@code newPassword} et persiste — jamais en clair, jamais loggé.
 */
public record PasswordChangeDto(
    @NotBlank(message = "currentPassword requis")
    @Size(min = 1, max = 64) String currentPassword,

    @NotBlank(message = "newPassword requis")
    @Size(min = 10, max = 100, message = "newPassword doit faire entre 10 et 100 caractères")  // P1 — min 10 (NIST)
    @Size(min = 1, max = 64) String newPassword
) {
}
