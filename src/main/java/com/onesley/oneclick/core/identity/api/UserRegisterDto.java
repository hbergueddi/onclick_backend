package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO d'inscription PUBLIQUE — POST /api/users/register (permitAll).
 *
 * <p>Volontairement SANS {@code roleId} : le serveur force le rôle CLIENT
 * ({@code UserService.register}). Un visiteur anonyme ne doit jamais pouvoir
 * s'auto-attribuer un rôle privilégié (anti escalade de privilèges).</p>
 *
 * <p>La création admin (avec rôle arbitraire) reste sur POST /api/users
 * ({@link UserCreateDto}, authentifié + {@code @NotNull roleId}).</p>
 */
public record UserRegisterDto(
    UUID tenantId,
    @Email @NotBlank @Size(min = 1, max = 256) String email,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") @Size(min = 1, max = 64) String phone,
    @NotBlank @Size(min = 10, max = 100) String password,  // P1 — min 10 (NIST length-first, sans composition)
    @NotBlank @Size(min = 1, max = 128) String firstName,
    @NotBlank @Size(min = 1, max = 128) String lastName,
    @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language,
    /** RGPD — consentement CGU/Politique de confidentialité au signup. Optionnel pour compat
     *  ascendante (clients pas encore à jour) : la trace {@code cgu_accepted_at} est persistée
     *  seulement si {@code true}. Le refus reste bloqué côté client ; durcissement serveur ultérieur. */
    Boolean cguAccepted
) {
}
