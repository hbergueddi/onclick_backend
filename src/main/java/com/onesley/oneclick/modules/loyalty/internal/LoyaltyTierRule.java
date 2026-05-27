package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleDto;
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
 * Palier de fidélité PLATEFORME (OneClick Lounge) — règle de conversion nommée
 * gérée par l'admin (FORGE). Introduit par la migration V43.
 *
 * <p>À distinguer des deux autres concepts loyalty :
 * <ul>
 * <li>{@link GainRule} — conversion PAR restaurant (1 règle / resto)</li>
 * <li>tier_thresholds — paliers client PAR points (seuil → nom de tier)</li>
 * </ul>
 *
 * <p>Ressource globale (pas de {@code tenant_id} ni {@code restaurant_id}) :
 * l'admin plateforme les gère pour tous les tenants.
 */
@Entity
@Table(name = "loyalty_tier_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyTierRule extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    @Setter private String name;

    @Column(name = "description")
    @Setter private String description;

    @Column(name = "type", nullable = false)
    @Setter private String type = "standard";

    @Column(name = "conversion_rate", nullable = false, precision = 6, scale = 4)
    @Setter private BigDecimal conversionRate = new BigDecimal("0.1000");

    @Column(name = "min_ticket", nullable = false, precision = 10, scale = 2)
    @Setter private BigDecimal minTicket = BigDecimal.ZERO;

    @Column(name = "max_points_per_ticket", nullable = false)
    @Setter private int maxPointsPerTicket = 500;

    @Column(name = "period_type", nullable = false)
    @Setter private String periodType = "month";

    @Column(name = "period_value", nullable = false)
    @Setter private int periodValue = 1;

    @Column(name = "benefit_duration_days", nullable = false)
    @Setter private int benefitDurationDays = 90;

    @Column(name = "min_spend_monthly", nullable = false, precision = 10, scale = 2)
    @Setter private BigDecimal minSpendMonthly = BigDecimal.ZERO;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public LoyaltyTierRule(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public LoyaltyTierRuleDto toDto() {
        return new LoyaltyTierRuleDto(
            id, name, description, type,
            conversionRate, minTicket, maxPointsPerTicket,
            periodType, periodValue, benefitDurationDays, minSpendMonthly,
            enabled, getCreatedAt(), getUpdatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoyaltyTierRule that = (LoyaltyTierRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
