package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Post membre du réseau social privé (Circle). C4.8c — socle modération.
 *
 * <p>{@code tenant_id} + {@code author_id} immuables (renseignés à la création membre — flux futur).
 * La modération mute uniquement {@code status} / {@code rejection_reason} / {@code reviewed_at} /
 * {@code reviewed_by} / {@code deleted_at} (soft-delete).
 */
@Entity
@Table(name = "member_posts")
@Getter
@NoArgsConstructor
public class MemberPost extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "activity_tag", length = 32)
    private String activityTag;

    @Column(name = "status", nullable = false, length = 16)
    @Setter private String status = "pending";

    @Column(name = "rejection_reason", length = 512)
    @Setter private String rejectionReason;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;

    /** A.2 — UUID (text[]) des membres mentionnés dans le post. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "mentioned_user_ids", columnDefinition = "text[]")
    @Setter private String[] mentionedUserIds;

    /**
     * Constructeur de création membre (A.1) : status par défaut {@code pending}
     * (modération a priori). {@code tenant_id}/{@code author_id} immuables ; id généré par l'appelant.
     */
    public MemberPost(UUID id, UUID tenantId, UUID authorId, String content,
                      String photoUrl, String activityTag) {
        this.id = id;
        this.tenantId = tenantId;
        this.authorId = authorId;
        this.content = content;
        this.photoUrl = photoUrl;
        this.activityTag = activityTag;
        this.status = "pending";
    }
}
