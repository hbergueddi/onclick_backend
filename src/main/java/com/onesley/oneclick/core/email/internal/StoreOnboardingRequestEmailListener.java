package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canal EMAIL de la <b>soumission</b> d'une demande d'inscription d'enseigne (BE-1 — plan
 * RESTAURANT-ONBOARDING). Écoute {@link StoreOnboardingRequestedEvent} (publié par
 * {@code modules.store.StoreOnboardingService.create} APRÈS commit) et envoie via {@link ResendClient}
 * <b>deux</b> emails branded {@code oneclick} :
 * <ol>
 *   <li><b>copie interne</b> à l'équipe Onesley ({@code app.email.internal.onboarding}, défaut {@code contact@app-oneclick.net})
 *       — template {@code store-onboarding-request-internal}, avec lien
 *       direct vers la page admin de revue ;</li>
 *   <li><b>accusé de réception</b> au gérant ({@code ev.ownerEmail()}) — template
 *       {@code store-onboarding-request-received}.</li>
 * </ol>
 *
 * <p>Frontière Modulith : {@code modules.store} ne dépend pas de {@code core.email} ; l'event (dans
 * {@code shared}) porte tout le payload (nom resto, ville, gérant, email). Stub-safe : si
 * {@code RESEND_API_KEY} absent, {@link ResendClient} no-op loggé (pas d'exception). La copie interne
 * est une adresse de service (jamais en suppression-list en pratique) ; l'accusé gérant est, lui,
 * filtré par la suppression-list de {@link ResendClient}. Complète (ne remplace pas) la notif in-app
 * admin produite par {@code core.notification} sur le même event.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StoreOnboardingRequestEmailListener {

    /** Les enseignes candidates rejoignent le tenant public OneClick. */
    private static final String BRAND_SLUG = "oneclick";

    private final EmailTemplateService templateService;
    private final ResendClient resendClient;

    /** Boîte interne Onesley qui reçoit chaque nouvelle demande (override prod APP_EMAIL_INTERNAL_ONBOARDING). */
    @Value("${app.email.internal.onboarding:contact@app-oneclick.net}")
    private String internalRecipient;

    /** Base URL du Command Center admin pour le lien « Examiner la demande » (override prod APP_FRONTEND_ADMIN_BASE_URL). */
    @Value("${app.frontend.admin-base-url:https://admin.app-oneclick.net}")
    private String adminBaseUrl;

    @ApplicationModuleListener
    void onStoreOnboardingRequested(StoreOnboardingRequestedEvent ev) {
        String business = (ev.restaurantName() == null || ev.restaurantName().isBlank())
            ? "une enseigne" : ev.restaurantName();
        String contactName = ev.contactName() == null ? "" : ev.contactName();
        String city = ev.city() == null ? "" : ev.city();

        // 1) Copie interne équipe Onesley — toujours envoyée (adresse de service).
        String adminLink = adminBaseUrl + "/forge/demandes-inscription?highlight=" + ev.requestId();
        Map<String, Object> internalVars = new HashMap<>();
        internalVars.put("businessName", business);
        internalVars.put("city", city);
        internalVars.put("contactName", contactName);
        internalVars.put("ownerEmail", ev.ownerEmail() == null ? "" : ev.ownerEmail());
        internalVars.put("adminLink", adminLink);
        String internalSubject = truncate("Nouvelle demande de partenariat — " + business
            + (city.isBlank() ? "" : " (" + city + ")"), 128);
        String internalHtml = templateService.render(
            BRAND_SLUG, "store-onboarding-request-internal", internalVars, internalSubject);
        resendClient.send(new EmailSendDto(
            "store-onboarding-request-internal", BRAND_SLUG, List.of(internalRecipient),
            internalSubject, null, internalVars), internalHtml);

        // 2) Accusé de réception au gérant (si email fourni).
        if (ev.ownerEmail() != null && !ev.ownerEmail().isBlank()) {
            String firstName = (ev.ownerFirstName() == null || ev.ownerFirstName().isBlank())
                ? contactName : ev.ownerFirstName();
            Map<String, Object> ackVars = new HashMap<>();
            ackVars.put("businessName", business);
            ackVars.put("firstName", firstName);
            ackVars.put("contactName", contactName);
            String ackSubject = truncate("Votre demande de partenariat a bien été reçue — " + business, 128);
            String ackHtml = templateService.render(
                BRAND_SLUG, "store-onboarding-request-received", ackVars, ackSubject);
            resendClient.send(new EmailSendDto(
                "store-onboarding-request-received", BRAND_SLUG, List.of(ev.ownerEmail()),
                ackSubject, null, ackVars), ackHtml);
        } else {
            log.warn("[store-onboarding/email] requested={} sans email gérant — accusé non envoyé", ev.requestId());
        }

        log.info("[store-onboarding/email] requested={} (business={}) — copie interne + accusé traités",
            ev.requestId(), business);
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
