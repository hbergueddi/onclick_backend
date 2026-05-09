package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.audit.CreatedAtEntity;
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
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.redemption_otp_requests} — workflow OTP avant consommation
 * de points (au-dessus du seuil défini par gain_rules).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code staff_id NOT NULL} → {@link Profile} (le staff qui demande l'OTP) en
 *       {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 */
@Entity
@Table(name = "redemption_otp_requests")
public class RedemptionOtpRequest extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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

    @Column(name = "staff_id", nullable = false, insertable = false, updatable = false)
    private UUID staffId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Profile staff;

    @NotNull
    @Column(name = "points_requested", nullable = false)
    private Integer pointsRequested;

    @NotNull
    @Column(name = "ticket_montant", nullable = false)
    private BigDecimal ticketMontant;

    @NotNull
    @Column(name = "estimated_discount_dh", nullable = false)
    private BigDecimal estimatedDiscountDh;

    @NotBlank
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @NotNull
    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_for_ticket_ref")
    private String consumedForTicketRef;

    protected RedemptionOtpRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getStaffId() { return staffId; }
    public Profile getStaff() { return staff; }
    public void setStaff(Profile staff) { this.staff = staff; }
    public Integer getPointsRequested() { return pointsRequested; }
    public BigDecimal getTicketMontant() { return ticketMontant; }
    public BigDecimal getEstimatedDiscountDh() { return estimatedDiscountDh; }
    public String getCodeHash() { return codeHash; }
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getConsumedForTicketRef() { return consumedForTicketRef; }

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
        RedemptionOtpRequest that = (RedemptionOtpRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
