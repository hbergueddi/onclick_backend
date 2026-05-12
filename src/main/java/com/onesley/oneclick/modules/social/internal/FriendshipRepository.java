package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
