package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand {@code AccountActivationService} (core/auth) a créé une
 * invitation d'activation de compte membre (Gap #10). Consommé par
 * {@code MemberEnrollmentInviteEmailListener} (core/email) qui envoie le lien
 * magique branded vers {@code /accept-invite?token=...}.
 *
 * <p>Pattern event server-side identique à {@link TenantAdminInvitedEvent} : la
 * frontière Modulith interdit à {@code core.auth} de dépendre de {@code core.email}.
 * On porte donc sur l'event TOUT ce dont l'email a besoin (slug, nom, destinataire,
 * token clair, expiration), sans aucune lecture cross-module dans le listener.</p>
 *
 * <p><b>Sécurité</b> : {@code rawToken} est le token EN CLAIR — il n'existe qu'en
 * mémoire (l'event n'est jamais persisté) et ne sert qu'à composer le lien email.
 * La base ne stocke que son SHA-256 ({@code AccountActivationInvite.tokenHash}).</p>
 *
 * @param inviteId   id de l'invitation créée
 * @param userId     compte membre cible
 * @param email      destinataire
 * @param firstName  prénom (personnalisation email)
 * @param tenantSlug slug tenant (branding email + lien)
 * @param tenantName nom commercial tenant (corps email)
 * @param rawToken   token clair (lien magique) — transient, jamais persisté
 * @param expiresAt  expiration du lien (copie email)
 * @param occurredAt horodatage de création
 */
public record MemberEnrollmentInvitedEvent(
    UUID inviteId,
    UUID userId,
    String email,
    String firstName,
    String tenantSlug,
    String tenantName,
    String rawToken,
    Instant expiresAt,
    Instant occurredAt
) {
}
