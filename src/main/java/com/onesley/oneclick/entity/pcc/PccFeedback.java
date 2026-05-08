package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.pcc_feedbacks} — feedbacks membres PCC scopés par
 * restaurant (workflow notif owners + email Resend + thread reply).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code member_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code target_restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)},
 *       nullable (feedback global tenant si non renseigné).</li>
 *   <li>{@code reply_by} : audit field (qui a répondu — Adil ou autre owner), reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "pcc_feedbacks")
public class PccFeedback extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false, insertable = false, updatable = false)
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Profile member;

    @NotBlank
    @Column(name = "sentiment", nullable = false)
    private String sentiment;

    @NotBlank
    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "comment")
    private String comment;

    @Column(name = "reply_text")
    private String replyText;

    /** Audit field : UUID brut. */
    @Column(name = "reply_by")
    private UUID replyBy;

    @Column(name = "reply_at")
    private Instant replyAt;

    @Column(name = "reply_read_by_member")
    private Boolean replyReadByMember;

    @Column(name = "target_restaurant_id", insertable = false, updatable = false)
    private UUID targetRestaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_restaurant_id")
    private Restaurant targetRestaurant;

    protected PccFeedback() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public Profile getMember() { return member; }
    public void setMember(Profile member) { this.member = member; }
    public String getSentiment() { return sentiment; }
    public String getCategory() { return category; }
    public String getComment() { return comment; }
    public String getReplyText() { return replyText; }
    public UUID getReplyBy() { return replyBy; }
    public Instant getReplyAt() { return replyAt; }
    public Boolean getReplyReadByMember() { return replyReadByMember; }
    public UUID getTargetRestaurantId() { return targetRestaurantId; }
    public Restaurant getTargetRestaurant() { return targetRestaurant; }
    public void setTargetRestaurant(Restaurant targetRestaurant) { this.targetRestaurant = targetRestaurant; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        PccFeedback that = (PccFeedback) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
