package com.onesley.oneclick.modules.announcement.internal;

import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.AnnouncementDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publisher temps réel STOMP des annonces tenant (Lot 8).
 *
 * <p>Temps réel = WebSocket STOMP (jamais polling), calqué sur l'infra existante
 * ({@code AbstractDashboardPublisher} / {@code DisputeDashboardPublisher} / {@code FeedbackPublisher}).
 * Contrairement aux dashboards (snapshot agrégé périodique poussé sur empreinte), une annonce est un
 * <b>événement discret</b> : on pousse le {@link AnnouncementDto} dès la mutation (création /
 * publication / édition), via {@link SimpMessagingTemplate}. Le front du staff invalide sa query /
 * met à jour sa bannière (pas de re-fetch en boucle).
 *
 * <h3>Topic par tenant</h3>
 * <p>{@link #topicFor(UUID)} = {@code /topic/announcements/{tenantId}} : chaque tenant a son canal
 * (isolation), les apps staff s'abonnent au topic de LEUR tenant. Le {@code tenantId} dans le chemin
 * suffit à la frontière (pattern broadcast par-tenant aligné sur {@code FeedbackPublisher} ; la
 * frontière fine reste l'autorisation REST côté lecture). On NE pousse PAS sur le topic à
 * l'archivage/suppression (la disparition est gérée par le re-fetch déclenché côté front), mais on
 * pousse à la création/publication/édition pour la bannière live.</p>
 *
 * <p>Best-effort : un échec de push ne casse jamais la transaction métier (la persistance de
 * l'annonce + la notif in-app sont le livrable réel). Le service appelle ces méthodes APRÈS save.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnnouncementPublisher {

    /** Préfixe du topic par-tenant. Le chemin complet = préfixe + tenantId. */
    static final String TOPIC_PREFIX = "/topic/announcements/";

    private final SimpMessagingTemplate messagingTemplate;

    /** Topic STOMP complet du tenant. Public pour réutilisation côté front/tests. */
    public static String topicFor(UUID tenantId) {
        return TOPIC_PREFIX + tenantId;
    }

    /**
     * Push d'une annonce (création / publication / édition) sur le topic de son tenant.
     * No-op si {@code tenantId} ou {@code dto} null. Best-effort (log warn sur échec).
     */
    public void publish(UUID tenantId, AnnouncementDto dto) {
        if (tenantId == null || dto == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(topicFor(tenantId), dto);
        } catch (RuntimeException e) {
            log.warn("[ws] announcement push échoué (announcement={}, tenant={}): {}",
                dto.id(), tenantId, e.getMessage());
        }
    }
}
