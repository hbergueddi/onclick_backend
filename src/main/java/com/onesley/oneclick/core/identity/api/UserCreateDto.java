package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO de création pour POST /api/users — payload signup ou création admin.
 *
 * <p>Le {@code password} en clair est hashé dans le service (BCrypt).
 * Le {@code tenantId} est optionnel pour les admins plateforme.
 */
public record UserCreateDto(
    UUID tenantId,
    @NotNull UUID roleId,
    @Email @NotBlank @Size(min = 1, max = 256) String email,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") @Size(min = 1, max = 64) String phone,
    @NotBlank @Size(min = 10, max = 100) @Size(min = 1, max = 64) String password,  // P1 — min 10 (NIST length-first)
    @NotBlank @Size(max = 100) @Size(min = 1, max = 128) String firstName,
    @NotBlank @Size(max = 100) @Size(min = 1, max = 128) String lastName,
    @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language
) {
}
