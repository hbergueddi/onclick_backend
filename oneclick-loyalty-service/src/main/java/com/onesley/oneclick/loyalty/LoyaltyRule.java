package com.onesley.oneclick.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Règle de calcul des points par restaurant.
 *
 * <p>Conversion : {@code points = floor(amount * conversion_rate)}, plafonné à {@code max_points}.
 * Minimum {@code min_ticket_amount} en dessous duquel rien n'est crédité.
 */
@Entity
@Table(name = "loyalty_rules")
public class LoyaltyRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @DecimalMin("0.0000")
    @Column(name = "conversion_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal conversionRate = new BigDecimal("0.0500");

    @NotNull
    @Min(1)
    @Column(name = "max_points", nullable = false)
    private Integer maxPoints = 1000;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "min_ticket_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minTicketAmount = new BigDecimal("100.00");

    @NotNull
    @DecimalMin("0.0000")
    @Column(name = "point_value", nullable = false, precision = 8, scale = 4)
    private BigDecimal pointValue = new BigDecimal("1.0000");

    @Min(1)
    @Column(name = "expires_after_days", nullable = false)
    private Integer expiresAfterDays = 365;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected LoyaltyRule() {
        // JPA
    }

    public LoyaltyRule(UUID id, UUID restaurantId) {
        this.id = id;
        this.restaurantId = restaurantId;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public BigDecimal getConversionRate() { return conversionRate; }
    public void setConversionRate(BigDecimal conversionRate) { this.conversionRate = conversionRate; }
    public Integer getMaxPoints() { return maxPoints; }
    public void setMaxPoints(Integer maxPoints) { this.maxPoints = maxPoints; }
    public BigDecimal getMinTicketAmount() { return minTicketAmount; }
    public void setMinTicketAmount(BigDecimal minTicketAmount) { this.minTicketAmount = minTicketAmount; }
    public BigDecimal getPointValue() { return pointValue; }
    public void setPointValue(BigDecimal pointValue) { this.pointValue = pointValue; }
    public Integer getExpiresAfterDays() { return expiresAfterDays; }
    public void setExpiresAfterDays(Integer expiresAfterDays) { this.expiresAfterDays = expiresAfterDays; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoyaltyRule that = (LoyaltyRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
