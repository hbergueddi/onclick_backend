package com.onesley.oneclick.core.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository {@link Notification} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    List<Notification> findAllByRecipientUserId(UUID recipientUserId);

    /** Cloche notification : liste complète pour un user, triée created_at DESC. */
    List<Notification> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    /** Cloche notification : liste non lues pour un user, triée created_at DESC. */
    List<Notification> findAllByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientUserId);

    /** Badge cloche : nombre de notifications non lues pour un user. */
    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    /**
     * Bulk update : marque toutes les notifications non lues d'un user comme lues.
     *
     * @return nombre de lignes mises à jour
     */
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    int markAllReadByRecipientUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
