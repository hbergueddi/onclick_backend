package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link BusinessHour} — accès CRUD + finders dérivés.
 *
 * <p>Pas de soft delete sur {@code business_hours} (extends TimestampedEntity) :
 * le remplacement complet (PUT) supprime puis réinsère les lignes d'une entité.
 */
@Repository
public interface BusinessHourRepository extends JpaRepository<BusinessHour, UUID>, JpaSpecificationExecutor<BusinessHour> {

    /** Horaires d'une entité (restaurant/resource), triés jour puis heure de début. */
    List<BusinessHour> findByEntityTypeAndEntityIdOrderByDayOfWeekAscStartTimeAsc(String entityType, UUID entityId);

    /** Purge des horaires d'une entité (remplacement complet PUT). */
    long deleteByEntityTypeAndEntityId(String entityType, UUID entityId);
}
