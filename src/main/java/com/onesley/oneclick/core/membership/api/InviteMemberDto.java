package com.onesley.oneclick.core.membership.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête d'invitation d'un membre dans un programme (P2 — admin tenant).
 *
 * <p>L'admin invite par {@code email} (clé de réutilisation/création du <b>compte
 * OneClick unique</b>). Si un compte existe déjà (email OU téléphone), il est réutilisé
 * (les noms fournis sont ignorés) ; sinon un compte CLIENT est créé en tenant home
 * {@code oneclick} — d'où {@code firstName}/{@code lastName} requis à la création (validés
 * côté service, comme {@code EnrollmentService}). Jamais de 2ᵉ compte (invariant P0).</p>
 *
 * @param email      destinataire / clé compte (obligatoire)
 * @param phone      téléphone optionnel (clé de réutilisation secondaire)
 * @param firstName  prénom (requis si création d'un nouveau compte)
 * @param lastName   nom (requis si création d'un nouveau compte)
 * @param memberType libellé métier optionnel du type de membre (ex. "titulaire", "conjoint")
 */
public record InviteMemberDto(
    @NotBlank @Email @Size(max = 255) String email,
    @Size(max = 32) String phone,
    @Size(max = 120) String firstName,
    @Size(max = 120) String lastName,
    @Size(max = 32) String memberType
) {
}
