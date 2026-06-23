package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canal EMAIL des avis membres (gap #6, parité legacy {@code send-pcc-feedback} /
 * {@code send-pcc-feedback-reply} qui envoyaient un email Resend en plus de la notif in-app).
 *
 * <p>La notif in-app est déjà produite par {@code NotificationEventHandler} (core/notification) ;
 * ce listener ajoute l'email brandé via {@link ResendClient} (stub-safe si {@code RESEND_API_KEY}
 * absent, suppression bounce déjà gérée par {@code ResendClient}). Pattern event server-side
 * identique à {@code MemberEnrollmentInviteEmailListener}.
 *
 * <ul>
 *   <li>{@link FeedbackCreatedEvent} → email aux destinataires (owners du resto ciblé +
 *       tenant-admins, résolus côté feedback et portés sur l'event).</li>
 *   <li>{@link FeedbackRepliedEvent} → email au membre auteur de l'avis.</li>
 * </ul>
 *
 * <p>HTML inline (pas de fichier template requis) ; branding par slug tenant résolu via
 * {@link TenantDirectoryApi#slugById} ; emails résolus via {@link UserDirectoryApi#nameById}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class FeedbackEmailListener {

    private final UserDirectoryApi userDirectory;
    private final TenantDirectoryApi tenantDirectory;
    private final ResendClient resendClient;

    @Value("${app.frontend.base-url:https://app-oneclick.net}")
    private String frontendBaseUrl;

    @ApplicationModuleListener
    void onFeedbackCreated(FeedbackCreatedEvent ev) {
        if (ev.recipientUserIds() == null || ev.recipientUserIds().isEmpty()) return;
        List<String> emails = ev.recipientUserIds().stream()
            .map(this::emailOf)
            .filter(e -> e != null && !e.isBlank())
            .distinct()
            .toList();
        if (emails.isEmpty()) return;

        String slug = slugOf(ev.tenantId());
        String subject = "happy".equals(ev.sentiment()) ? "Nouvel avis positif d'un membre 💚"
                                                         : "Nouvel avis à traiter 🟠";
        String link = frontendBaseUrl + "/prodesk/pcc-feedbacks";
        String body = "Un membre vient de laisser un avis"
            + (ev.category() != null ? " (" + escape(ev.category()) + ")" : "")
            + ". Connectez-vous à votre espace pour le consulter et y répondre.";
        String htmlBody = html(subject, body, "Voir l'avis", link);

        EmailSendDto dto = new EmailSendDto(
            "pcc-feedback-created", slug, emails, truncate(subject, 128), null,
            Map.of("link", link, "sentiment", ev.sentiment() == null ? "" : ev.sentiment()));
        var result = resendClient.send(dto, htmlBody);
        log.info("[feedback/email] created feedback={} tenant={} recipients={} sent={}",
            ev.feedbackId(), slug, emails.size(), result.sent());
    }

    @ApplicationModuleListener
    void onFeedbackReplied(FeedbackRepliedEvent ev) {
        String email = emailOf(ev.memberId());
        if (email == null || email.isBlank()) return;

        String slug = slugOf(ev.tenantId());
        String subject = "Réponse à votre avis ✍️";
        String link = frontendBaseUrl + "/pocket/pcc/feedback?thread=" + ev.feedbackId();
        String body = "Notre équipe a répondu à votre avis. Touchez pour lire la réponse dans l'application.";
        String htmlBody = html(subject, body, "Lire la réponse", link);

        EmailSendDto dto = new EmailSendDto(
            "pcc-feedback-reply", slug, List.of(email), subject, null, Map.of("link", link));
        var result = resendClient.send(dto, htmlBody);
        log.info("[feedback/email] reply feedback={} tenant={} member={} sent={}",
            ev.feedbackId(), slug, ev.memberId(), result.sent());
    }

    private String emailOf(UUID userId) {
        return userId == null ? null : userDirectory.nameById(userId).map(UserDirectoryApi.UserName::email).orElse(null);
    }

    /** Slug du tenant pour le branding ; fallback {@code default} (brand OneClick) si introuvable. */
    private String slugOf(UUID tenantId) {
        return tenantDirectory.slugById(tenantId).filter(s -> !s.isBlank()).orElse("default");
    }

    /** HTML minimal brandé (inline) — évite un fichier template dédié pour ce canal secondaire. */
    private static String html(String heading, String body, String ctaLabel, String ctaUrl) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:24px;color:#1a1a1a">
              <h2 style="margin:0 0 12px">%s</h2>
              <p style="font-size:15px;line-height:1.5;color:#444">%s</p>
              <p style="margin:24px 0">
                <a href="%s" style="background:#714B67;color:#fff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:bold">%s</a>
              </p>
              <p style="font-size:12px;color:#999">OneClick — Fidélité, réservations et offres.</p>
            </div>
            """.formatted(escape(heading), escape(body), ctaUrl, escape(ctaLabel));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
