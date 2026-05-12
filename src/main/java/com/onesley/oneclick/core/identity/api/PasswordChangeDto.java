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
    String currentPassword,

    @NotBlank(message = "newPassword requis")
    @Size(min = 8, max = 100, message = "newPassword doit faire entre 8 et 100 caractères")
    String newPassword
) {
}
