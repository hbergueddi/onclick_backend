package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * BE-5 (plan RESTAURANT-ONBOARDING) — publisher temps réel STOMP de la file admin d'onboarding.
 *
 * <p>Temps réel = WebSocket STOMP (jamais polling), aligné sur l'infra existante
 * ({@code FeedbackPublisher} / {@code DisputeDashboardPublisher}). À la soumission d'une demande
 * d'inscription resto, on pousse le {@link OnboardingRequestDto} sur {@link #TOPIC} pour que la page
 * de revue admin (Forge) voie la nouvelle demande apparaître <b>en direct</b> (sans rafraîchir).
 *
 * <p>{@code SimpMessagingTemplate} est un bean framework (pas un type de module) → aucune dépendance
 * Modulith ajoutée. Best-effort : un échec de push ne casse jamais la transaction métier (la
 * persistance de la demande + la notif in-app admin + les emails sont le livrable réel).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StoreOnboardingPublisher {

    /** Topic broadcast de la file admin d'onboarding (le front Forge s'y abonne). */
    public static final String TOPIC = "/topic/admin/onboarding";

    private final SimpMessagingTemplate messagingTemplate;

    /** Push d'une nouvelle demande d'inscription sur la file admin temps réel. */
    public void publishNew(OnboardingRequestDto dto) {
        try {
            messagingTemplate.convertAndSend(TOPIC, dto);
        } catch (RuntimeException e) {
            log.warn("[ws] store-onboarding new push échoué (request={}): {}", dto.id(), e.getMessage());
        }
    }
}
