package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Règle de calcul des points par restaurant.
 *
 * <p>Conversion : {@code points = floor(amount * conversion_rate)}, plafonné à {@code max_points}.
 * Minimum {@code min_ticket_amount} en dessous duquel rien n'est crédité.
 */
@Entity
@Table(name = "loyalty_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "conversion_rate", nullable = false, precision = 5, scale = 4)
    @Setter private BigDecimal conversionRate = new BigDecimal("0.0500");

    @Column(name = "max_points", nullable = false)
    @Setter private Integer maxPoints = 1000;

    @Column(name = "min_ticket_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal minTicketAmount = new BigDecimal("100.00");

    @Column(name = "point_value", nullable = false, precision = 8, scale = 4)
    @Setter private BigDecimal pointValue = new BigDecimal("1.0000");

    @Column(name = "expires_after_days", nullable = false)
    @Setter private Integer expiresAfterDays = 365;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public LoyaltyRule(UUID id, UUID restaurantId) {
        this.id = id;
        this.restaurantId = restaurantId;
    }

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
