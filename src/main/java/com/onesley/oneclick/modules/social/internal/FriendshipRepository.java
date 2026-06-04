package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link Friendship} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID>, JpaSpecificationExecutor<Friendship> {
    java.util.List<Friendship> findAllByUser1Id(java.util.UUID user1Id);
    java.util.List<Friendship> findAllByUser2Id(java.util.UUID user2Id);

    /**
     * Nombre d'amitiés ACCEPTÉES dont {@code userId} est partie (user1 OU user2).
     * Sert au plafond d'amis (50) enforcé dans {@link SocialService#request}.
     * Convention canonique user1 &lt; user2 → 1 row par couple, pas de double comptage.
     */
    @Query("""
        SELECT COUNT(f) FROM Friendship f
        WHERE f.status = 'accepted'
          AND (f.user1Id = :userId OR f.user2Id = :userId)
        """)
    long countAcceptedFriendshipsOf(@Param("userId") java.util.UUID userId);
}
