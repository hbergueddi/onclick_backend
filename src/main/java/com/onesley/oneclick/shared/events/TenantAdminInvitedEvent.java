package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un SUPERADMIN crée une invitation tenant-admin
 * ({@code TenantAdminInviteService.createInvite}, E2 — V78).
 *
 * <p>Consommé par {@code TenantAdminInviteEmailListener} (core/email) qui envoie l'email
 * branded contenant le lien magique. Pattern event server-side identique à
 * {@link FeedbackCreatedEvent} : la frontière Modulith interdit à {@code core.tenant} de
 * dépendre de {@code core.email} (et inversement core.email dépend déjà de core.tenant).
 * On porte donc sur l'event TOUT ce dont l'email a besoin pour construire le lien et le
 * template, sans aucune lecture cross-module dans le listener.</p>
 *
 * <p><b>Sécurité</b> : {@code rawToken} est le token EN CLAIR — il n'existe qu'en mémoire
 * (l'event n'est jamais persisté) et ne sert qu'à composer le lien email. La base ne stocke
 * que son SHA-256 ({@code TenantAdminInvite.tokenHash}).</p>
 *
 * @param inviteId    id de l'invitation créée
 * @param tenantId    tenant cible
 * @param tenantSlug  slug du tenant (sélection du branding email + lien)
 * @param tenantName  nom commercial du tenant (corps de l'email)
 * @param email       destinataire
 * @param rawToken    token clair (lien magique) — transient, jamais persisté
 * @param expiresAt   expiration du lien (copie email)
 * @param occurredAt  horodatage de création
 */
public record TenantAdminInvitedEvent(
    UUID inviteId,
    UUID tenantId,
    String tenantSlug,
    String tenantName,
    String email,
    String rawToken,
    Instant expiresAt,
    Instant occurredAt
) {
}
