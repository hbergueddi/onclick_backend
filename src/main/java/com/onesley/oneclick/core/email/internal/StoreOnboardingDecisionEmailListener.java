package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canal EMAIL de la décision d'onboarding d'enseigne (Lot B5, port de l'EF legacy
 * {@code send-onboarding-decision} qui envoyait un email VPS au gérant avec le verdict).
 *
 * <p>Écoute {@link StoreOnboardingDecidedEvent} (publié par {@code modules.store} APRÈS commit) et
 * envoie un email <b>branded</b> au gérant via {@link ResendClient} + template
 * {@code store-onboarding-decision-approved} / {@code store-onboarding-decision-rejected} (fallback
 * stub {@link EmailTemplateService} si le fichier n'est pas livré). Pattern event server-side
 * identique à {@code MemberEnrollmentInviteEmailListener} / {@code TenantAdminInviteEmailListener} :
 * la frontière Modulith interdit à {@code modules.store} de dépendre de {@code core.email} ; l'event
 * (dans {@code shared}) porte tout ce dont l'email a besoin (email, nom, nom resto, verdict, motif,
 * lien) — aucune lecture cross-module ici.</p>
 *
 * <p><b>Kill-switch / fallback</b> : si {@code RESEND_API_KEY} est absent, {@link ResendClient}
 * passe en mode stub (no-op loggé) — aucune exception. Si le destinataire est absent, on skip
 * proprement. Branding {@code oneclick} (les enseignes candidates rejoignent le tenant public).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StoreOnboardingDecisionEmailListener {

    /** Slug de branding : les demandes d'enseigne rejoignent le tenant public OneClick. */
    private static final String BRAND_SLUG = "oneclick";

    private final EmailTemplateService templateService;
    private final ResendClient resendClient;

    @ApplicationModuleListener
    void onStoreOnboardingDecided(StoreOnboardingDecidedEvent ev) {
        if (ev.contactEmail() == null || ev.contactEmail().isBlank()) {
            log.warn("[store-onboarding/email] decision={} sans email destinataire — skip", ev.requestId());
            return;
        }

        String business = (ev.businessName() == null || ev.businessName().isBlank())
            ? "votre enseigne" : ev.businessName();
        String template = ev.approved()
            ? "store-onboarding-decision-approved" : "store-onboarding-decision-rejected";
        String subject = ev.approved()
            ? "Votre demande d'enseigne a été approuvée — " + business
            : "Votre demande d'enseigne — " + business;

        Map<String, Object> vars = new HashMap<>();
        vars.put("businessName", business);
        vars.put("contactName", ev.contactName() == null ? "" : ev.contactName());
        vars.put("email", ev.contactEmail());
        if (ev.approved()) {
            vars.put("loginLink", ev.loginUrl() == null ? "" : ev.loginUrl());
            // BE-2 — identifiants de 1re connexion : email + mot de passe temporaire (à changer).
            vars.put("loginEmail", ev.contactEmail());
            vars.put("tempPassword", ev.tempPassword() == null ? "" : ev.tempPassword());
        } else {
            vars.put("rejectionReason",
                (ev.rejectionReason() == null || ev.rejectionReason().isBlank())
                    ? "Votre dossier ne répond pas à nos critères actuels."
                    : ev.rejectionReason());
        }

        String html = templateService.render(BRAND_SLUG, template, vars, subject);
        EmailSendDto dto = new EmailSendDto(
            template, BRAND_SLUG, List.of(ev.contactEmail()),
            truncate(subject, 128), null, vars);

        var result = resendClient.send(dto, html);
        log.info("[store-onboarding/email] decision={} approved={} to={} sent={}",
            ev.requestId(), ev.approved(), ev.contactEmail(), result.sent());
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
