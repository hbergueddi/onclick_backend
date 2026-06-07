package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Commentaire d'un post du mur communautaire (A.2).
 *
 * <p>{@code mentioned_user_ids} : text[] d'UUID (pattern tags/allergens du schéma Spring).
 * Pas de soft-delete (suppression physique d'un commentaire).
 */
@Entity
@Table(name = "member_post_comments")
@Getter
@NoArgsConstructor
public class MemberPostComment extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "mentioned_user_ids", columnDefinition = "text[]")
    private String[] mentionedUserIds;

    public MemberPostComment(UUID id, UUID postId, UUID authorId, String content, String[] mentionedUserIds) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.mentionedUserIds = mentionedUserIds;
    }
}
