package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Règle de gain de points PAR restaurant — override des {@link LoyaltyRule} globaux.
 *
 * <p>Modèle introduit par la migration V13 (Sprint B) :
 * <ul>
 * <li>{@code conversion_rate} — taux MAD → points (0..1)</li>
 * <li>{@code cap_per_visit} — plafond points par visite (optionnel)</li>
 * <li>{@code cap_per_month} — plafond points par mois (optionnel)</li>
 * <li>{@code min_amount} — montant minimum ticket éligible (optionnel)</li>
 * </ul>
 *
 * <p>{@code tenant_id} est rempli par trigger DB depuis {@code restaurants.tenant_id}
 * (cf. {@code fill_tenant_id_from_restaurant_for_gain_rules}) → mapping read-only
 * côté Hibernate (insertable=false, updatable=false).
 *
 * <p>Contrainte UNIQUE sur {@code restaurant_id} : 1 règle active par restaurant.
 */
@Entity
@Table(
    name = "gain_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GainRule extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    // tenant_id rempli automatiquement par trigger DB V13 (depuis restaurants.tenant_id)
    // → read-only côté Hibernate
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "conversion_rate", nullable = false, precision = 6, scale = 4)
    @Setter private BigDecimal conversionRate = new BigDecimal("0.1000");

    @Column(name = "cap_per_visit")
    @Setter private Integer capPerVisit;

    @Column(name = "cap_per_month")
    @Setter private Integer capPerMonth;

    @Column(name = "min_amount", precision = 10, scale = 2)
    @Setter private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Setter private boolean isActive = true;

    /**
     * Bonus de bienvenue par défaut crédité à l'inscription d'un nouveau membre
     * (cf. V28 + EnrollmentService). Pré-rempli dans le formulaire EnrollMember.
     */
    
    @Column(name = "welcome_points_default", nullable = false)
    @Setter private int welcomePointsDefault = 100;

    /**
     * Plafond du bonus de bienvenue. EnrollmentService rejette toute demande
     * &gt; welcomePointsMax (anti-abus staff). DB CHECK garantit max &gt;= default.
     */
    
    @Column(name = "welcome_points_max", nullable = false)
    @Setter private int welcomePointsMax = 500;

    public GainRule(UUID id, UUID restaurantId, BigDecimal conversionRate) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.conversionRate = conversionRate;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public GainRuleDto toDto() {
        return new GainRuleDto(
            id, restaurantId, conversionRate,
            capPerVisit, capPerMonth, minAmount,
            isActive, welcomePointsDefault, welcomePointsMax, getCreatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        GainRule that = (GainRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
