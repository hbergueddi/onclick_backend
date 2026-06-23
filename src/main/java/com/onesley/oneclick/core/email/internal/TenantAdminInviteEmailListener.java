package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.TenantAdminInvitedEvent;
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
 * Envoi de l'email d'invitation tenant-admin (E2 — V78).
 *
 * <p>Écoute {@link TenantAdminInvitedEvent} (publié par {@code core.tenant} APRÈS commit) et
 * envoie le lien magique branded via {@link ResendClient} + template {@code tenant-admin-invite}.
 * Pattern event server-side : {@code core.tenant} ne dépend pas de {@code core.email} ; l'event
 * (dans {@code shared}) porte tout ce dont l'email a besoin (slug, nom, destinataire, token clair,
 * expiration). Stub-safe si {@code RESEND_API_KEY} absent (no-op loggé).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantAdminInviteEmailListener {

    private static final DateTimeFormatter EXPIRY_FMT =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH).withZone(ZoneId.of("Africa/Casablanca"));

    private final EmailTemplateService templateService;
    private final ResendClient resendClient;

    /**
     * Base URL du Command Center admin pour composer le lien magique d'invitation tenant-admin
     * (l'invité accède au back-office) — override prod via APP_FRONTEND_ADMIN_BASE_URL.
     */
    @Value("${app.frontend.admin-base-url:https://admin.app-oneclick.net}")
    private String adminBaseUrl;

    @ApplicationModuleListener
    void onTenantAdminInvited(TenantAdminInvitedEvent ev) {
        String link = adminBaseUrl + "/onboarding/welcome?token="
            + URLEncoder.encode(ev.rawToken(), StandardCharsets.UTF_8);
        String subject = "Invitation administrateur — " + ev.tenantName();

        Map<String, Object> vars = Map.of(
            "tenantName", ev.tenantName(),
            "inviteLink", link,
            "email", ev.email(),
            "expiresAt", EXPIRY_FMT.format(ev.expiresAt())
        );

        String html = templateService.render(ev.tenantSlug(), "tenant-admin-invite", vars, subject);
        EmailSendDto dto = new EmailSendDto(
            "tenant-admin-invite", ev.tenantSlug(), List.of(ev.email()),
            subject, "Admin invitation — " + ev.tenantName(), vars);

        var result = resendClient.send(dto, html);
        log.info("[tenant-admin-invite/email] invite={} tenant={} to={} sent={}",
            ev.inviteId(), ev.tenantSlug(), ev.email(), result.sent());
    }
}
