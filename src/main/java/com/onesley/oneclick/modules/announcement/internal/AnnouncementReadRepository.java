package com.onesley.oneclick.modules.announcement.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link AnnouncementRead} (Lot 8) — acquittements par couple {@code (announcement, user)}.
 *
 * <p>Sert à :
 * <ul>
 *   <li>l'upsert idempotent du mark-read ({@link #findByAnnouncementIdAndUserId}) ;</li>
 *   <li>joindre l'état « lu/non-lu » du caller à une liste d'annonces sans N+1
 *       ({@link #findReadAnnouncementIdsForUser}) — le service compare la version acquittée à
 *       {@code Announcement.bodyVersion} ;</li>
 *   <li>purger les acquittements d'une annonce dont le corps a été édité
 *       ({@link #deleteByAnnouncementId}) → ré-acquittement requis (bump body_version).</li>
 * </ul>
 */
@Repository
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadId> {

    /** Acquittement existant d'un couple (annonce, user) — base de l'upsert idempotent. */
    Optional<AnnouncementRead> findByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    /**
     * Acquittements du {@code userId} pour un ensemble d'annonces — projection (announcementId,
     * bodyVersionRead) pour calculer l'état lu/non-lu par version sans N+1. On filtre sur les ids
     * de la page courante d'annonces (jamais toute la table).
     */
    @Query("SELECT r.announcementId, r.bodyVersionRead FROM AnnouncementRead r "
        + "WHERE r.userId = :userId AND r.announcementId IN :announcementIds")
    List<Object[]> findReadAnnouncementIdsForUser(@Param("userId") UUID userId,
                                                  @Param("announcementIds") List<UUID> announcementIds);

    /**
     * Purge tous les acquittements d'une annonce (appelé quand son corps est édité → bump
     * body_version → ré-acquittement requis). {@code @Modifying} + clear automatique du contexte
     * de persistance pour rester cohérent après le DELETE bulk.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AnnouncementRead r WHERE r.announcementId = :announcementId")
    void deleteByAnnouncementId(@Param("announcementId") UUID announcementId);
}
