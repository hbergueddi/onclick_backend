package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.pcc_feedbacks} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "pcc_feedbacks")
public class PccFeedback extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "sentiment", nullable = false)
    private String sentiment;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "comment")
    private String comment;

    @Column(name = "reply_text")
    private String replyText;

    @Column(name = "reply_by")
    private UUID replyBy;

    @Column(name = "reply_at")
    private Instant replyAt;

    @Column(name = "reply_read_by_member")
    private Boolean replyReadByMember;

    @Column(name = "target_restaurant_id")
    private UUID targetRestaurantId;

    protected PccFeedback() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public String getSentiment() { return sentiment; }
    public String getCategory() { return category; }
    public String getComment() { return comment; }
    public String getReplyText() { return replyText; }
    public UUID getReplyBy() { return replyBy; }
    public Instant getReplyAt() { return replyAt; }
    public Boolean getReplyReadByMember() { return replyReadByMember; }
    public UUID getTargetRestaurantId() { return targetRestaurantId; }
}
