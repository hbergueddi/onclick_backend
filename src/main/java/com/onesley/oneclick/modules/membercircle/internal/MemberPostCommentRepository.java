package com.onesley.oneclick.modules.membercircle.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Repository {@link MemberPostComment} — A.2. */
@Repository
public interface MemberPostCommentRepository extends JpaRepository<MemberPostComment, UUID> {
    long countByPostId(UUID postId);
}
