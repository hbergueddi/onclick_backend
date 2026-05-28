package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventCreateDto;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Service du domaine « cycle de vie restaurant » (lifecycle_events).
 *
 * <p>Lecture admin (RBAC LIFECYCLE côté contrôleur) + journalisation append-only.
 * Le nom du restaurant est résolu intra-module via {@link RestaurantRepository}
 * (pas de couplage cross-module ni de jointure native fragile).
 */
@Service
@RequiredArgsConstructor
public class LifecycleEventService {

    private final LifecycleEventRepository repository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LifecycleEventDto> list() {
        List<LifecycleEvent> events = repository.findAllByOrderByCreatedAtDesc();
        List<UUID> ids = events.stream()
            .map(LifecycleEvent::getRestaurantId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<UUID, String> names = ids.isEmpty()
            ? Map.of()
            : restaurantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
        return events.stream()
            .map(e -> e.toDto(e.getRestaurantId() == null ? null : names.get(e.getRestaurantId())))
            .toList();
    }

    @Transactional
    public LifecycleEventDto create(LifecycleEventCreateDto dto) {
        LifecycleEvent e = new LifecycleEvent(UUID.randomUUID(), dto.event().trim());
        e.setRestaurantId(dto.restaurantId());
        if (dto.details() != null) e.setDetails(dto.details());
        if (dto.actor() != null) e.setActor(dto.actor());
        LifecycleEvent saved = repository.save(e);
        String name = dto.restaurantId() == null
            ? null
            : restaurantRepository.findById(dto.restaurantId()).map(Restaurant::getName).orElse(null);
        return saved.toDto(name);
    }

    /**
     * Journalise un événement de cycle de vie déclenché par une mutation interne
     * du catalogue (inscription / suspension / réactivation / rejet / modification).
     *
     * <p>L'acteur est résolu depuis le contexte de sécurité courant (admin/owner
     * authentifié). Participe à la transaction de l'appelant (propagation REQUIRED) :
     * si la mutation restaurant est rollback, l'entrée de journal l'est aussi —
     * pas de log orphelin, pas de mutation sans trace. Append-only.
     *
     * <p>Producteur de {@code lifecycle_events} (V45) : avant ce câblage, le
     * journal restait vide (la page admin CycleDeVie affichait toujours « aucun
     * événement »).
     *
     * @param eventType clé d'événement FR alignée sur {@code eventConfig} du front
     *                  (inscription / suspension / réactivation / rejet / modification)
     * @param restaurantId restaurant concerné (jamais null pour les producteurs catalogue)
     * @param details libellé lisible (non validé : appel interne, colonne TEXT)
     */
    @Transactional
    public void record(String eventType, UUID restaurantId, String details) {
        LifecycleEvent e = new LifecycleEvent(UUID.randomUUID(), eventType.trim());
        e.setRestaurantId(restaurantId);
        e.setDetails(details);
        e.setActor(resolveActor());
        repository.save(e);
    }

    /** Nom lisible de l'acteur courant (« Prénom Nom »), sinon « Système » (pas de contexte JWT). */
    private String resolveActor() {
        UUID uid = SecurityHelper.currentUserId();
        if (uid == null) return "Système";
        return userRepository.findById(uid)
            .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
            .orElse("Système");
    }
}
