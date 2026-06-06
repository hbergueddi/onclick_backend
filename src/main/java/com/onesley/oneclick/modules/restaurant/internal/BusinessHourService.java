package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHourDto;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHoursPutDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service horaires d'ouverture restaurant — table normalisée {@code business_hours} (E1).
 *
 * <p>Source de vérité pour l'éditeur d'horaires (wizard onboarding + cockpit).
 * Le PUT fait un <b>remplacement complet</b> de la semaine du restaurant
 * (DELETE puis INSERT) — atomique.
 *
 * <p>{@code entity_type = "restaurant"} fixe (table polymorphique partagée avec
 * les ressources PCC). Garde-fou {@code endTime > startTime} (CHECK DB) renvoyé
 * en 400 explicite plutôt qu'en 500 Hibernate.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BusinessHourService {

    static final String ENTITY_TYPE = "restaurant";

    private final BusinessHourRepository repository;
    private final RestaurantRepository restaurantRepository;

    /** Horaires structurés d'un restaurant (triés jour puis heure). */
    public List<BusinessHourDto> getForRestaurant(UUID restaurantId) {
        requireRestaurant(restaurantId);
        return repository
            .findByEntityTypeAndEntityIdOrderByDayOfWeekAscStartTimeAsc(ENTITY_TYPE, restaurantId)
            .stream().map(BusinessHourService::toDto).toList();
    }

    /** Remplace intégralement les horaires d'un restaurant (PUT). */
    @Transactional
    public List<BusinessHourDto> replaceForRestaurant(UUID restaurantId, BusinessHoursPutDto dto) {
        requireRestaurant(restaurantId);
        // Garde-fou métier : la DB exige end_time > start_time (CHECK) → 400 lisible.
        dto.hours().forEach(h -> {
            if (!h.endTime().isAfter(h.startTime())) {
                throw new BadRequestException(
                    "L'heure de fermeture doit être après l'ouverture (jour " + h.dayOfWeek() + ").");
            }
        });
        // Remplacement complet : purge puis réinsertion (flush pour ordonner DELETE avant INSERT).
        repository.deleteByEntityTypeAndEntityId(ENTITY_TYPE, restaurantId);
        repository.flush();
        List<BusinessHour> rows = dto.hours().stream()
            .map(h -> new BusinessHour(UUID.randomUUID(), ENTITY_TYPE, restaurantId,
                h.dayOfWeek(), h.startTime(), h.endTime()))
            .toList();
        return repository.saveAll(rows).stream().map(BusinessHourService::toDto).toList();
    }

    private void requireRestaurant(UUID restaurantId) {
        restaurantRepository.findById(restaurantId)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", restaurantId));
    }

    static BusinessHourDto toDto(BusinessHour h) {
        return new BusinessHourDto(h.getId(), h.getDayOfWeek(), h.getStartTime(), h.getEndTime());
    }
}
