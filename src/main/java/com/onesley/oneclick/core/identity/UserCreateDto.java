package com.onesley.oneclick.core.identity;

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
    @Email @NotBlank String email,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") String phone,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Pattern(regexp = "^(fr|en|ar)$") String language
) {
}
