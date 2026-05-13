package com.onesley.oneclick.modules.event.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import com.onesley.oneclick.modules.event.api.Event;

/**
 * Repository {@link Event} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
    java.util.List<Event> findAllByTenantId(java.util.UUID tenantId);
    java.util.List<Event> findAllByRestaurantId(java.util.UUID restaurantId);

    /**
     * Sprint D — Events Elite actifs : is_active=true + futurs.
     * Tri event_at ASC pour afficher les prochains en premier (Pocket EliteClub).
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT e FROM Event e
        WHERE e.deletedAt IS NULL
          AND e.isActive = true
          AND e.eventAt > CURRENT_TIMESTAMP
        ORDER BY e.eventAt ASC
        """)
    java.util.List<Event> findEliteActiveUpcoming();

    /**
     * Sprint D — Tous les events Elite (admin dashboard).
     * Tri event_at DESC pour avoir les plus récents en premier.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT e FROM Event e
        WHERE e.deletedAt IS NULL
        ORDER BY e.eventAt DESC
        """)
    java.util.List<Event> findAllElite();
}
