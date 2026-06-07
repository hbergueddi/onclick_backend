package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un staff inscrit un <b>nouveau</b> membre (compte créé côté
 * serveur avec mot de passe aléatoire) ET demande l'envoi du lien d'activation
 * ({@code EnrollmentService.enrollMember} avec {@code sendInvite=true}). Gap #10.
 *
 * <p>Consommé par {@code AccountActivationService} (core/auth) qui crée une
 * invitation d'activation (token hashé, single-use, 7j) et émet à son tour
 * {@link MemberEnrollmentInvitedEvent} (porteur du token clair) pour le listener
 * email. On découple ainsi loyalty → auth → email uniquement via {@code shared}
 * (module OPEN) : aucune frontière Modulith franchie en compile-time.</p>
 *
 * <p>L'event porte le {@code tenantSlug}/{@code tenantName} (résolus par loyalty
 * depuis le restaurant) pour que ni le listener auth ni le listener email n'aient
 * à faire de lecture cross-module — même philosophie que {@link TenantAdminInvitedEvent}.</p>
 *
 * @param userId       compte membre fraîchement créé (cible de l'activation)
 * @param email        destinataire du lien magique
 * @param firstName    prénom (personnalisation email + dialog)
 * @param tenantId     tenant du restaurant (héritage)
 * @param tenantSlug   slug tenant (branding email + lien) — {@code null}/oneclick si standard
 * @param tenantName   nom commercial tenant (corps email)
 * @param invitedBy    staff/admin déclencheur (audit)
 * @param occurredAt   horodatage
 */
public record MemberEnrollmentRequestedEvent(
    UUID userId,
    String email,
    String firstName,
    UUID tenantId,
    String tenantSlug,
    String tenantName,
    UUID invitedBy,
    Instant occurredAt
) {
}
