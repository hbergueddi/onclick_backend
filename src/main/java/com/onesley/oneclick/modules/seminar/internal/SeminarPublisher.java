package com.onesley.oneclick.modules.seminar.internal;

import com.onesley.oneclick.modules.seminar.api.SeminarDtos.SeminarRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publisher temps réel STOMP des demandes de séminaire (PCC).
 *
 * <p>Temps réel = WebSocket STOMP (jamais polling), calqué sur {@code FeedbackPublisher} /
 * {@code AbstractDashboardPublisher}. Une demande est un <b>événement discret</b> : on pousse le
 * {@link SeminarRequestDto} dès la mutation (création / changement de statut). Le front invalide
 * sa query / met à jour sa liste (pas de re-fetch en boucle).
 *
 * <h3>Deux destinations</h3>
 * <ul>
 *   <li>{@link #TOPIC_CREATED} {@code /topic/seminars} — broadcast à la création + au changement
 *       de statut, pour l'<b>inbox commercial</b> (staff/admin). Le front commercial filtre côté
 *       client par tenant (la frontière fine reste la lecture REST staff-scopée {@code /inbox}).</li>
 *   <li>{@code /topic/seminars/user/{organizerId}} — au changement de statut, pour le <b>membre</b>
 *       organisateur qui suit l'avancement de SA demande (pattern aligné sur {@code FeedbackPublisher}).</li>
 * </ul>
 *
 * <p>Best-effort : un échec de push ne casse jamais la transaction métier (la persistance + la
 * notif in-app sont le livrable réel). Le service appelle ces méthodes APRÈS save.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeminarPublisher {

    /** Broadcast création / changement de statut — inbox commercial (staff/admin). */
    public static final String TOPIC_CREATED = "/topic/seminars";

    /** Préfixe du topic par-membre (avancement de SA demande). Chemin complet = préfixe + organizerId. */
    static final String TOPIC_USER_PREFIX = "/topic/seminars/user/";

    private final SimpMessagingTemplate messagingTemplate;

    /** Topic par-membre complet. Public pour réutilisation côté front/tests. */
    public static String userTopic(UUID organizerId) {
        return TOPIC_USER_PREFIX + organizerId;
    }

    /** Push d'une création / mise à jour sur le topic commercial (inbox). */
    public void publishToInbox(SeminarRequestDto dto) {
        try {
            messagingTemplate.convertAndSend(TOPIC_CREATED, dto);
        } catch (RuntimeException e) {
            log.warn("[ws] seminar inbox push échoué (seminar={}): {}", dto.id(), e.getMessage());
        }
    }

    /** Push d'un changement de statut sur le topic du membre organisateur. */
    public void publishToOrganizer(UUID organizerId, SeminarRequestDto dto) {
        if (organizerId == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(userTopic(organizerId), dto);
        } catch (RuntimeException e) {
            log.warn("[ws] seminar status push échoué (seminar={}, organizer={}): {}",
                dto.id(), organizerId, e.getMessage());
        }
    }
}
