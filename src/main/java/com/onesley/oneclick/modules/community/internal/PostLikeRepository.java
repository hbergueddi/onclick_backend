package com.onesley.oneclick.modules.community.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link PostLike} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID>, JpaSpecificationExecutor<PostLike> {
    java.util.List<PostLike> findAllByPostId(java.util.UUID postId);
    java.util.List<PostLike> findAllByUserId(java.util.UUID userId);
}
