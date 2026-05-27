package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.LifecycleEventCreateDto;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventDto;
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
}
