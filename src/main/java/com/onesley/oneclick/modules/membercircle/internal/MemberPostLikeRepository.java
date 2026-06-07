package com.onesley.oneclick.modules.membercircle.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Repository {@link MemberPostLike} — toggle like (A.1). */
@Repository
public interface MemberPostLikeRepository extends JpaRepository<MemberPostLike, UUID> {

    Optional<MemberPostLike> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}
