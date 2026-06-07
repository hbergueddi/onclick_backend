package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Like d'un post du mur communautaire membre (A.1).
 *
 * <p>1 like par {@code (post_id, user_id)} (UNIQUE en base) → idempotent. Pas de soft-delete :
 * un « unlike » supprime physiquement la ligne (V80).
 */
@Entity
@Table(name = "member_post_likes")
@Getter
@NoArgsConstructor
public class MemberPostLike extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    public MemberPostLike(UUID id, UUID postId, UUID userId) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
    }
}
