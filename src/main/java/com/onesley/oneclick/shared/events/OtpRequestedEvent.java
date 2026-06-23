package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand {@code AuthService.requestOtp} a généré et persisté un code OTP
 * (P1 enrollment — vérification email au signup, mais générique pour tous les purposes).
 * Consommé par {@code OtpEmailListener} (core/email) qui envoie le code par email branded
 * via Resend — uniquement pour les purposes à livraison email (signup, reset_password,
 * verify_email, 2fa). Les purposes non-email (verify_phone → SMS, redemption → staff)
 * sont ignorés par le listener.
 *
 * <p>Pattern event server-side identique à {@link MemberEnrollmentInvitedEvent} : la
 * frontière Modulith interdit à {@code core.auth} de dépendre de {@code core.email}.
 * On porte donc sur l'event TOUT ce dont l'email a besoin (code, destinataire, prénom,
 * slug/nom tenant pour le branding, expiration), sans aucune lecture cross-module.</p>
 *
 * <p><b>Sécurité</b> : {@code code} est le code OTP EN CLAIR — il n'existe qu'en mémoire
 * (l'event n'est jamais persisté hors event_publication, lui-même purgé à la complétion)
 * et ne sert qu'à composer l'email. La base stocke le code dans {@code otp_requests}
 * (usage unique, TTL 10 min).</p>
 *
 * @param otpId       id de l'OTP créé
 * @param userId      compte cible
 * @param email       destinataire
 * @param firstName   prénom (personnalisation email)
 * @param purpose     {@code signup} | {@code reset_password} | {@code verify_email} | {@code 2fa} | ...
 * @param code        code OTP 6 chiffres en clair — transient
 * @param tenantSlug  slug tenant (branding email), {@code default} si user global
 * @param tenantName  nom commercial tenant (corps email), {@code OneClick} par défaut
 * @param expiresAt   expiration du code (copie email)
 * @param occurredAt  horodatage de création
 */
public record OtpRequestedEvent(
    UUID otpId,
    UUID userId,
    String email,
    String firstName,
    String purpose,
    String code,
    String tenantSlug,
    String tenantName,
    Instant expiresAt,
    Instant occurredAt
) {
}
