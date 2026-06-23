package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code StoreOnboardingService.decide} quand un admin tranche une demande
 * d'inscription d'enseigne (approuvée / refusée — port de l'EF legacy
 * {@code send-onboarding-decision}).
 *
 * <p>Consommé par {@code StoreOnboardingDecisionEmailListener} (core/email) qui envoie un email
 * <b>branded</b> au gérant via {@code ResendClient} : « Votre demande d'enseigne a été approuvée /
 * refusée », avec un lien de connexion si approuvé (et le motif si refusé). Stub-safe / kill-switch
 * si Resend indisponible (legacy = email VPS). Pas de push.
 *
 * <p><b>Frontière Modulith</b> : la frontière interdit à {@code modules.store} de dépendre de
 * {@code core.email}. On porte donc sur l'event TOUT ce dont l'email a besoin (destinataire, nom,
 * nom resto, verdict, motif, lien) — aucune lecture cross-module dans le listener. Pattern event
 * server-side identique à {@link MemberEnrollmentInvitedEvent} / {@link TenantAdminInvitedEvent}.
 *
 * @param requestId       id de la demande d'onboarding décidée
 * @param contactEmail    email du gérant demandeur (destinataire)
 * @param contactName     nom du gérant demandeur (personnalisation), peut être null
 * @param businessName    nom commercial du resto candidat (corps de l'email)
 * @param approved        {@code true} = approuvée, {@code false} = refusée
 * @param rejectionReason motif de refus (corps de l'email si refusée), null si approuvée
 * @param loginUrl        lien de connexion à inclure si approuvé (sinon null/ignoré)
 * @param tempPassword    mot de passe temporaire généré à l'approbation (BE-2) — affiché dans l'email
 *                        d'approbation ; {@code null} si refusée. À changer au 1er login (BE-3).
 * @param occurredAt      horodatage de la décision
 */
public record StoreOnboardingDecidedEvent(
    UUID requestId,
    String contactEmail,
    String contactName,
    String businessName,
    boolean approved,
    String rejectionReason,
    String loginUrl,
    String tempPassword,
    Instant occurredAt
) {
}
