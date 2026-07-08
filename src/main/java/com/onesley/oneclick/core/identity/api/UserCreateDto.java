package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO d'<b>upsert</b> pour POST /api/users — payload signup / création admin / mise à jour.
 *
 * <p>Le champ {@code id} pilote le mode dans {@code UserService.create} :
 * <ul>
 *   <li>{@code id == null} → <b>création</b> (premier accès) ;</li>
 *   <li>{@code id} renseigné → <b>mise à jour</b> de l'utilisateur existant.</li>
 * </ul>
 *
 * <p>Le {@code password} en clair est hashé dans le service (BCrypt). Il est <b>obligatoire en
 * création</b> (garde explicite dans le service) et <b>optionnel en mise à jour</b> (inchangé s'il
 * est absent) — d'où l'absence de {@code @NotBlank} ici ; {@code @Size} continue d'imposer la
 * longueur minimale (P1 — min 10, NIST length-first) lorsqu'un mot de passe est fourni.
 * Le {@code tenantId} est optionnel pour les admins plateforme.
 */
public record UserCreateDto(
    UUID id,
    UUID tenantId,
    @NotNull UUID roleId,
    @Email @NotBlank @Size(min = 1, max = 256) String email,
    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$") @Size(min = 1, max = 64) String phone,
    @Size(min = 10, max = 100) @Size(min = 1, max = 64) String password,  // null autorisé (update) ; ≥10 si fourni
    @NotBlank @Size(max = 100) @Size(min = 1, max = 128) String firstName,
    @NotBlank @Size(max = 100) @Size(min = 1, max = 128) String lastName,
    @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language
) {

    /**
     * Constructeur historique (sans {@code id}) → délègue en mode <b>création</b>.
     * Conserve la compatibilité source des appelants Java existants (signup public, provisioning,
     * tests). La désérialisation JSON utilise le constructeur canonique (9 args) : {@code id}
     * absent du body → {@code null} → création.
     */
    public UserCreateDto(UUID tenantId, UUID roleId, String email, String phone,
                         String password, String firstName, String lastName, String language) {
        this(null, tenantId, roleId, email, phone, password, firstName, lastName, language);
    }
}
