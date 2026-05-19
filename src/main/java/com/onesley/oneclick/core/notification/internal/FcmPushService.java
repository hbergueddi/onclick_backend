package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM push service — port des Edge Functions Supabase {@code send-promo-push}
 * et {@code send-reservation-push}.
 *
 * <p>Phase B.8 V1 : <b>mode stub par défaut</b>. Si {@code app.fcm.project-id}
 * ou {@code app.fcm.service-account-json} ne sont pas définies (cas dev local
 * et CI sans secret Firebase), le service ne fait pas d'appel réseau —
 * il log + retourne {@code PushResultDto{ sent: 0, total: N, message: "FCM not configured" }}
 * de sorte que le frontend peut tester l'orchestration sans push réel.
 *
 * <p>La signature JWT RS256 service-account + appel FCM HTTP v1 sont laissés
 * en TODO Sprint B.8.4 (port direct du code Deno {@code getFCMAccessToken}).
 *
 * <p>Pour activer en prod :
 * <ol>
 *   <li>{@code export FCM_PROJECT_ID=oneclick-129bb}</li>
 *   <li>{@code export FCM_SERVICE_ACCOUNT_JSON='{...}'} (JSON service-account complet)</li>
 *   <li>Implémenter {@link #getAccessToken()} — JWT RS256 + exchange Google OAuth</li>
 * </ol>
 */
@Service
@Slf4j
public class FcmPushService {

    private final String projectId;
    private final String serviceAccountJson;
    private final DeviceTokenRepository tokenRepo;
    private final NotificationRepository notifRepo;
    private final RestClient restClient;

    public FcmPushService(
        @Value("${app.fcm.project-id:}") String projectId,
        @Value("${app.fcm.service-account-json:}") String serviceAccountJson,
        DeviceTokenRepository tokenRepo,
        NotificationRepository notifRepo
    ) {
        this.projectId = projectId;
        this.serviceAccountJson = serviceAccountJson;
        this.tokenRepo = tokenRepo;
        this.notifRepo = notifRepo;
        this.restClient = RestClient.builder().build();
    }

    /** {@code true} si {@code app.fcm.*} sont définis — sinon mode stub. */
    public boolean isConfigured() {
        return projectId != null && !projectId.isBlank()
            && serviceAccountJson != null && !serviceAccountJson.isBlank();
    }

    /**
     * Envoie un push promo aux users cibles (port {@code send-promo-push}).
     *
     * <p>Pour chaque user :
     * <ol>
     *   <li>Lookup ses {@link DeviceToken} actifs</li>
     *   <li>Envoie FCM HTTP v1 ({@code projects/{projectId}/messages:send}) si configuré</li>
     *   <li>Sinon log + skip (mode stub)</li>
     * </ol>
     */
    public PushResultDto sendPromo(PushPromoDto dto) {
        List<DeviceToken> tokens = new ArrayList<>();
        for (UUID userId : dto.userIds()) {
            tokens.addAll(tokenRepo.findAllByUserId(userId));
        }

        if (!isConfigured()) {
            log.warn("[fcm-push] promo — FCM non configuré, skip (total tokens={}, campaign={})",
                tokens.size(), dto.campaignId());
            return new PushResultDto(0, tokens.size(), List.of(), "FCM not configured");
        }

        return fanOutFcm(tokens, dto.title(), dto.body(), dto.link());
    }

    /**
     * Envoie un push résa au user cible (port {@code send-reservation-push}).
     */
    public PushResultDto sendReservation(PushReservationDto dto) {
        List<DeviceToken> tokens = tokenRepo.findAllByUserId(dto.recipientUserId());

        if (!isConfigured()) {
            log.warn("[fcm-push] reservation — FCM non configuré, skip (recipient={}, reservation={}, status={}, tokens={})",
                dto.recipientUserId(), dto.reservationId(), dto.status(), tokens.size());
            return new PushResultDto(0, tokens.size(), List.of(), "FCM not configured");
        }

        return fanOutFcm(tokens, dto.title(), dto.body(), dto.link());
    }

    /**
     * Fan-out FCM HTTP v1 — un appel par token.
     *
     * <p>TODO Sprint B.8.4 : implémenter {@link #getAccessToken()} pour activer
     * réellement l'envoi. Pour l'instant on retourne stub même si {@link #isConfigured()}
     * est {@code true}, parce que la signature JWT RS256 n'est pas portée.
     */
    private PushResultDto fanOutFcm(List<DeviceToken> tokens, String title, String body, String link) {
        // TODO Sprint B.8.4 : signature JWT RS256 + exchange OAuth Google
        // String accessToken = getAccessToken();
        log.info("[fcm-push] TODO B.8.4 — JWT RS256 + FCM HTTP v1 send (tokens={}, title='{}')",
            tokens.size(), title);

        int sent = 0;
        List<String> failures = new ArrayList<>();
        for (DeviceToken t : tokens) {
            try {
                // Placeholder — quand getAccessToken() sera implémenté, décommenter :
                // sendOne(accessToken, t, title, body, link);
                sent++;
                t.markUsed();
            } catch (RestClientException e) {
                failures.add(t.getToken() + ": " + e.getMessage());
            }
        }
        return new PushResultDto(sent, tokens.size(), failures, "FCM stub — JWT signature TODO B.8.4");
    }

    /**
     * TODO Sprint B.8.4 — Génère un access token Google OAuth pour FCM HTTP v1.
     *
     * <p>Doit :
     * <ol>
     *   <li>Parser {@code serviceAccountJson} → extraire {@code client_email} + {@code private_key}</li>
     *   <li>Construire un JWT RS256 (header + payload : {@code iss, scope, aud, iat, exp})</li>
     *   <li>Signer avec la clé privée RSA</li>
     *   <li>POST {@code https://oauth2.googleapis.com/token} avec
     *       {@code grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer}</li>
     *   <li>Retourner {@code access_token} de la réponse</li>
     * </ol>
     *
     * Voir l'implémentation Deno legacy : {@code supabase/functions/send-promo-push/index.ts}.
     */
    @SuppressWarnings("unused")
    private String getAccessToken() {
        throw new UnsupportedOperationException("TODO Sprint B.8.4 — JWT RS256 + Google OAuth");
    }

    /**
     * TODO Sprint B.8.4 — POST FCM HTTP v1 pour un token.
     *
     * <p>Endpoint : {@code https://fcm.googleapis.com/v1/projects/{projectId}/messages:send}
     */
    @SuppressWarnings("unused")
    private void sendOne(String accessToken, DeviceToken token, String title, String body, String link) {
        Map<String, Object> message = Map.of(
            "message", Map.of(
                "token", token.getToken(),
                "notification", Map.of("title", title, "body", body),
                "data", link != null ? Map.of("link", link) : Map.of()
            )
        );
        restClient.post()
            .uri("https://fcm.googleapis.com/v1/projects/{projectId}/messages:send", projectId)
            .header("Authorization", "Bearer " + accessToken)
            .body(message)
            .retrieve()
            .toBodilessEntity();
    }
}
