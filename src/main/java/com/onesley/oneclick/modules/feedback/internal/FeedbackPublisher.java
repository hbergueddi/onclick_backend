package com.onesley.oneclick.modules.feedback.internal;

import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.FeedbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publisher temps réel STOMP du thread « Avis » (PCC Lot 7).
 *
 * <p>Temps réel = WebSocket STOMP (jamais polling), calqué sur l'infra existante
 * ({@code AbstractDashboardPublisher} / {@code DisputeDashboardPublisher} / {@code GroupDashboardPublisher}).
 * Contrairement aux dashboards (snapshot agrégé périodique poussé sur empreinte), un avis est un
 * <b>événement discret</b> : on pousse le {@link FeedbackDto} dès la mutation (création / réponse),
 * via {@link SimpMessagingTemplate}. Le front invalide sa query / met à jour son thread (pas de
 * re-fetch en boucle).
 *
 * <h3>Deux destinations</h3>
 * <ul>
 *   <li>{@link #TOPIC_CREATED} {@code /topic/pcc-feedbacks} — broadcast à la création, pour le
 *       <b>dashboard owner</b> (inbox temps réel). Le front owner filtre côté client par resto
 *       ciblé / tenant (la frontière fine reste la lecture REST owner-scopée).</li>
 *   <li>{@code /topic/pcc-feedbacks/user/{memberId}} — à la réponse, pour le <b>membre</b> qui
 *       attend la réponse d'Adil sur SON thread. Topic broadcast avec le {@code memberId} dans le
 *       chemin (le client s'abonne à son propre chemin) — pattern simple aligné sur la spec Lot 7
 *       (vs queue {@code /user/**}, réservée aux dashboards par-principal).</li>
 * </ul>
 *
 * <p>Best-effort : un échec de push ne doit jamais casser la transaction métier (la persistance du
 * feedback + la notif in-app sont le livrable réel). Le service appelle ces méthodes APRÈS save.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FeedbackPublisher {

    /** Broadcast création — dashboard owner (inbox temps réel). */
    public static final String TOPIC_CREATED = "/topic/pcc-feedbacks";

    /** Préfixe du topic par-membre (réponse owner → membre). Le chemin complet = préfixe + memberId. */
    static final String TOPIC_USER_PREFIX = "/topic/pcc-feedbacks/user/";

    private final SimpMessagingTemplate messagingTemplate;

    /** Topic par-membre complet (réponse). Public pour réutilisation côté front/tests. */
    public static String userTopic(UUID memberId) {
        return TOPIC_USER_PREFIX + memberId;
    }

    /** Push d'une création d'avis sur le topic owner (dashboard). */
    public void publishCreated(FeedbackDto dto) {
        try {
            messagingTemplate.convertAndSend(TOPIC_CREATED, dto);
        } catch (RuntimeException e) {
            log.warn("[ws] pcc-feedback created push échoué (feedback={}): {}", dto.id(), e.getMessage());
        }
    }

    /** Push d'une réponse d'avis sur le topic du membre destinataire. */
    public void publishReply(UUID memberId, FeedbackDto dto) {
        if (memberId == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(userTopic(memberId), dto);
        } catch (RuntimeException e) {
            log.warn("[ws] pcc-feedback reply push échoué (feedback={}, member={}): {}",
                dto.id(), memberId, e.getMessage());
        }
    }
}
