package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.audit.CreatedAtEntity;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.redemption_events} — log des consommations de points
 * (acceptées ou rejetées) avec flags anti-fraude.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code scanned_by} : audit field (qui a effectué le scan), reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "redemption_events")
public class RedemptionEvent extends CreatedAtEntity {

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

    /** Audit field : UUID brut. */
    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Column(name = "ticket_ref")
    private String ticketRef;

    @NotNull
    @Column(name = "ticket_montant", nullable = false)
    private BigDecimal ticketMontant;

    @NotNull
    @Column(name = "points_redeemed", nullable = false)
    private Integer pointsRedeemed;

    @NotNull
    @Column(name = "discount_dh", nullable = false)
    private BigDecimal discountDh;

    @Column(name = "client_tier")
    private String clientTier;

    @NotNull
    @Column(name = "effective_point_value_mad", nullable = false)
    private BigDecimal effectivePointValueMad;

    @NotNull
    @Column(name = "accepted", nullable = false)
    private Boolean accepted;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @NotNull
    @Column(name = "flag_ratio_high", nullable = false)
    private Boolean flagRatioHigh;

    @NotNull
    @Column(name = "flag_daily_near_cap", nullable = false)
    private Boolean flagDailyNearCap;

    @NotNull
    @Column(name = "flag_first_redemption", nullable = false)
    private Boolean flagFirstRedemption;

    @NotNull
    @Column(name = "flag_large_absolute", nullable = false)
    private Boolean flagLargeAbsolute;

    protected RedemptionEvent() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getScannedBy() { return scannedBy; }
    public String getTicketRef() { return ticketRef; }
    public BigDecimal getTicketMontant() { return ticketMontant; }
    public Integer getPointsRedeemed() { return pointsRedeemed; }
    public BigDecimal getDiscountDh() { return discountDh; }
    public String getClientTier() { return clientTier; }
    public BigDecimal getEffectivePointValueMad() { return effectivePointValueMad; }
    public Boolean getAccepted() { return accepted; }
    public String getRejectionReason() { return rejectionReason; }
    public Boolean getFlagRatioHigh() { return flagRatioHigh; }
    public Boolean getFlagDailyNearCap() { return flagDailyNearCap; }
    public Boolean getFlagFirstRedemption() { return flagFirstRedemption; }
    public Boolean getFlagLargeAbsolute() { return flagLargeAbsolute; }

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
        RedemptionEvent that = (RedemptionEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
