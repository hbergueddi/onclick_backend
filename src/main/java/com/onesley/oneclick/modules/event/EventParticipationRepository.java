package com.onesley.oneclick.modules.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link EventParticipation} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface EventParticipationRepository extends JpaRepository<EventParticipation, UUID>, JpaSpecificationExecutor<EventParticipation> {
    java.util.List<EventParticipation> findAllByEventId(java.util.UUID eventId);
    java.util.List<EventParticipation> findAllByUserId(java.util.UUID userId);
}
