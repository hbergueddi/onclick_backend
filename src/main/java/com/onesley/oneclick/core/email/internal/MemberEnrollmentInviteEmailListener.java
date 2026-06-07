package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.MemberEnrollmentInvitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envoi du lien magique d'activation de compte membre (Gap #10).
 *
 * <p>Écoute {@link MemberEnrollmentInvitedEvent} (publié par {@code core.auth} APRÈS commit) et
 * envoie le lien branded via {@link ResendClient} + template {@code member-enrollment-invite}
 * (fallback stub si non livré). Pattern event server-side identique à
 * {@code TenantAdminInviteEmailListener} : aucune lecture cross-module — l'event porte le slug,
 * le nom, le destinataire, le token clair et l'expiration. Stub-safe si {@code RESEND_API_KEY}
 * absent (no-op loggé).</p>
 *
 * <p>Le lien pointe vers {@code /accept-invite?token=...} (route {@code AuthFlowGate} côté front)
 * — équivalent Spring du {@code #type=invite} Supabase legacy.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class MemberEnrollmentInviteEmailListener {

    private static final DateTimeFormatter EXPIRY_FMT =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH).withZone(ZoneId.of("Africa/Casablanca"));

    private final EmailTemplateService templateService;
    private final ResendClient resendClient;

    /** Base URL frontend pour composer le lien magique (override prod via APP_FRONTEND_BASE_URL). */
    @Value("${app.frontend.base-url:https://app-oneclick.net}")
    private String frontendBaseUrl;

    @ApplicationModuleListener
    void onMemberEnrollmentInvited(MemberEnrollmentInvitedEvent ev) {
        String tenantName = (ev.tenantName() == null || ev.tenantName().isBlank()) ? "OneClick" : ev.tenantName();
        String slug = (ev.tenantSlug() == null || ev.tenantSlug().isBlank()) ? "default" : ev.tenantSlug();
        String link = frontendBaseUrl + "/accept-invite?token="
            + URLEncoder.encode(ev.rawToken(), StandardCharsets.UTF_8);
        String subject = "Activez votre compte — " + tenantName;

        Map<String, Object> vars = Map.of(
            "tenantName", tenantName,
            "firstName", ev.firstName() == null ? "" : ev.firstName(),
            "inviteLink", link,
            "email", ev.email(),
            "expiresAt", EXPIRY_FMT.format(ev.expiresAt())
        );

        String html = templateService.render(slug, "member-enrollment-invite", vars, subject);
        EmailSendDto dto = new EmailSendDto(
            "member-enrollment-invite", slug, List.of(ev.email()),
            subject, "Activez votre compte " + tenantName, vars);

        var result = resendClient.send(dto, html);
        log.info("[member-enrollment/email] invite={} user={} tenant={} to={} sent={}",
            ev.inviteId(), ev.userId(), slug, ev.email(), result.sent());
    }
}
