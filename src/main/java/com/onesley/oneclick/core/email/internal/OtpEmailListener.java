package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.OtpRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Envoi du code OTP par email (P1 enrollment — vérification email au signup).
 *
 * <p>Écoute {@link OtpRequestedEvent} (publié par {@code core.auth} APRÈS commit) et envoie
 * le code 6 chiffres branded via {@link ResendClient} + template {@code otp-code}
 * (fallback stub si non livré ; le corps {@code text} porte le code en clair pour que
 * l'email reste lisible même sans template HTML). Pattern event server-side identique à
 * {@code MemberEnrollmentInviteEmailListener} : aucune lecture cross-module — l'event porte
 * le code, le destinataire et le branding. Stub-safe si {@code RESEND_API_KEY} absent (no-op loggé).</p>
 *
 * <p><b>Canal email uniquement</b> : ce listener ne traite QUE les purposes à livraison email
 * ({@link #EMAIL_PURPOSES}). Les codes {@code verify_phone} (SMS) et {@code redemption}
 * (affiché au staff Snap2Earn, jamais emailé) sont ignorés — un futur listener SMS pourra
 * s'abonner au même event et filtrer {@code verify_phone}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OtpEmailListener {

    /** Purposes dont le code OTP se livre par email. Les autres sont ignorés par ce canal. */
    private static final Set<String> EMAIL_PURPOSES = Set.of("signup", "reset_password", "verify_email", "2fa");

    private static final DateTimeFormatter EXPIRY_FMT =
        DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH).withZone(ZoneId.of("Africa/Casablanca"));

    private final EmailTemplateService templateService;
    private final ResendClient resendClient;

    @ApplicationModuleListener
    void onOtpRequested(OtpRequestedEvent ev) {
        if (ev.purpose() == null || !EMAIL_PURPOSES.contains(ev.purpose())) {
            log.debug("[otp/email] skip purpose={} (canal non-email) otp={}", ev.purpose(), ev.otpId());
            return;
        }

        String tenantName = (ev.tenantName() == null || ev.tenantName().isBlank()) ? "OneClick" : ev.tenantName();
        String slug = (ev.tenantSlug() == null || ev.tenantSlug().isBlank()) ? "default" : ev.tenantSlug();
        String subject = "Votre code de vérification — " + tenantName;

        Map<String, Object> vars = Map.of(
            "tenantName", tenantName,
            "firstName", ev.firstName() == null ? "" : ev.firstName(),
            "code", ev.code(),
            "purpose", ev.purpose(),
            "expiresAt", EXPIRY_FMT.format(ev.expiresAt())
        );

        String html = templateService.render(slug, "otp-code", vars, subject);
        EmailSendDto dto = new EmailSendDto(
            "otp-code", slug, List.of(ev.email()),
            subject, "Votre code " + tenantName + " : " + ev.code(), vars);

        var result = resendClient.send(dto, html);
        log.info("[otp/email] otp={} user={} tenant={} purpose={} to={} sent={}",
            ev.otpId(), ev.userId(), slug, ev.purpose(), ev.email(), result.sent());
    }
}
