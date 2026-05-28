package com.onesley.oneclick.modules.event.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    java.util.Optional<EventParticipation> findByEventIdAndUserId(java.util.UUID eventId, java.util.UUID userId);

    /** Liste les RSVP d'un event avec le membre joint (évite N+1 sur toDto → user). */
    @Query("SELECT p FROM EventParticipation p JOIN FETCH p.user WHERE p.eventId = :eventId")
    java.util.List<EventParticipation> findAllByEventIdFetchUser(@Param("eventId") java.util.UUID eventId);

    /** Liste les RSVP d'un user avec le membre joint (évite N+1 sur toDto → user). */
    @Query("SELECT p FROM EventParticipation p JOIN FETCH p.user WHERE p.userId = :userId")
    java.util.List<EventParticipation> findAllByUserIdFetchUser(@Param("userId") java.util.UUID userId);
}
