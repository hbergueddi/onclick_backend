package com.onesley.oneclick.entity.marketing;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.status.EntityStatus;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.promo_notification_requests} — demandes restaurateur
 * pour push notification d'une offre (workflow review admin avant envoi).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code offer_id NOT NULL} → {@link Offer} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code requested_by} et {@code reviewed_by} : audit fields, restent UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "promo_notification_requests")
public class PromoNotificationRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "offer_id", nullable = false, insertable = false, updatable = false)
    private UUID offerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /** Audit field : UUID brut. */
    @NotNull
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @NotNull
    @Column(name = "target_segments", nullable = false, columnDefinition = "text[]")
    private List<String> targetSegments = new ArrayList<>();

    @Column(name = "admin_note")
    private String adminNote;

    /** Audit field : UUID brut. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    @Column(name = "push_sent_count")
    private Integer pushSentCount;

    @Column(name = "push_error")
    private String pushError;

    protected PromoNotificationRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public Offer getOffer() { return offer; }
    public void setOffer(Offer offer) { this.offer = offer; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public List<String> getTargetSegments() { return targetSegments; }
    public String getAdminNote() { return adminNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getPushSentAt() { return pushSentAt; }
    public Integer getPushSentCount() { return pushSentCount; }
    public String getPushError() { return pushError; }

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
        PromoNotificationRequest that = (PromoNotificationRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
