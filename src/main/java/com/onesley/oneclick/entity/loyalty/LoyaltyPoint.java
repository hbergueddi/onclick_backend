package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.loyalty_points} — points fidélité gagnés par client/resto
 * (issus du Snap2Earn ou bonus système).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code credited_by} / {@code created_by} / {@code modified_by} : audit
 *       fields, restent UUID brut (convention senior).</li>
 * </ul>
 */
@Entity
@Table(name = "loyalty_points")
public class LoyaltyPoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotNull
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "reason")
    private String reason;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "amount_ttc")
    private BigDecimal amountTtc;

    /** Audit field : UUID brut. */
    @Column(name = "credited_by")
    private UUID creditedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "remaining_points")
    private Integer remainingPoints;

    @Column(name = "notified_7d_at")
    private Instant notified7dAt;

    @Column(name = "notified_1d_at")
    private Instant notified1dAt;

    /** Audit field : UUID brut. */
    @Column(name = "created_by")
    private UUID createdBy;

    /** Audit field : UUID brut. */
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected LoyaltyPoint() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public Integer getPoints() { return points; }
    public String getReason() { return reason; }
    public Instant getEarnedAt() { return earnedAt; }
    public BigDecimal getAmountTtc() { return amountTtc; }
    public UUID getCreditedBy() { return creditedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getRemainingPoints() { return remainingPoints; }
    public Instant getNotified7dAt() { return notified7dAt; }
    public Instant getNotified1dAt() { return notified1dAt; }
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
        LoyaltyPoint that = (LoyaltyPoint) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
