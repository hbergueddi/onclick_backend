package com.onesley.oneclick.entity.marketing;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.referrals} — programme de parrainage (50 pts pour
 * parrain et filleul à l'activation).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code referrer_id NOT NULL} → {@link Profile} (parrain) en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code referred_user_id} → {@link Profile} (filleul, nullable tant que pas activé)
 *       en {@code @ManyToOne(LAZY)}.</li>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable
 *       (parrainage resto-restos partagé).</li>
 *   <li>{@code created_by} / {@code modified_by} : audit fields, UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "referrals")
@EntityListeners(AuditingEntityListener.class)
public class Referral {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "referrer_id", nullable = false, insertable = false, updatable = false)
    private UUID referrerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_id", nullable = false)
    private Profile referrer;

    @NotBlank
    @Column(name = "referred_phone", nullable = false)
    private String referredPhone;

    @Column(name = "referred_name")
    private String referredName;

    @Column(name = "referred_user_id", insertable = false, updatable = false)
    private UUID referredUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id")
    private Profile referredUser;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @NotNull
    @Column(name = "pts_awarded", nullable = false)
    private Integer ptsAwarded;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    /** Audit field : UUID brut. */
    @Column(name = "created_by")
    private UUID createdBy;

    /** Audit field : UUID brut. */
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected Referral() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getReferrerId() { return referrerId; }
    public Profile getReferrer() { return referrer; }
    public void setReferrer(Profile referrer) { this.referrer = referrer; }
    public String getReferredPhone() { return referredPhone; }
    public String getReferredName() { return referredName; }
    public UUID getReferredUserId() { return referredUserId; }
    public Profile getReferredUser() { return referredUser; }
    public void setReferredUser(Profile referredUser) { this.referredUser = referredUser; }
    public String getStatus() { return status; }
    public Integer getPtsAwarded() { return ptsAwarded; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }

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
        Referral that = (Referral) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
