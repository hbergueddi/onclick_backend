package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.loyalty.GainRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_gain_rules} — règles de gain de points
 * spécifiques à un restaurant (override des {@link GainRule} globales).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.
 *       Côté inverse : {@link Restaurant#getGainRules()} en cascade ALL + orphanRemoval.</li>
 *   <li>{@code source_rule_id} → {@link GainRule} en {@code @ManyToOne(LAZY)}, nullable
 *       (les règles override n'ont pas toujours une source globale).</li>
 * </ul>
 */
@Entity
@Table(name = "restaurant_gain_rules")
public class RestaurantGainRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure restaurant_id ─────────────────────────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @NotBlank
    @Column(name = "type", nullable = false)
    private String type;

    @NotNull
    @Column(name = "taux_conversion", nullable = false)
    private BigDecimal tauxConversion;

    @NotNull
    @Column(name = "min_ticket", nullable = false)
    private BigDecimal minTicket;

    @NotNull
    @Column(name = "max_points_par_ticket", nullable = false)
    private Integer maxPointsParTicket;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Digits(integer = 4, fraction = 2)
    @Column(name = "point_value_mad", precision = 6, scale = 2)
    private BigDecimal pointValueMad;

    // ─── Jointure source_rule_id (GainRule globale, nullable) ───────────────
    @Column(name = "source_rule_id", insertable = false, updatable = false)
    private UUID sourceRuleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_rule_id")
    private GainRule sourceRule;

    @NotNull
    @Column(name = "welcome_points_default", nullable = false)
    private Integer welcomePointsDefault;

    @NotNull
    @Column(name = "welcome_points_max", nullable = false)
    private Integer welcomePointsMax;

    protected RestaurantGainRule() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestaurantId() { return restaurantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public BigDecimal getTauxConversion() { return tauxConversion; }
    public BigDecimal getMinTicket() { return minTicket; }
    public Integer getMaxPointsParTicket() { return maxPointsParTicket; }
    public Boolean getEnabled() { return enabled; }
    public BigDecimal getPointValueMad() { return pointValueMad; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getSourceRuleId() { return sourceRuleId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public GainRule getSourceRule() { return sourceRule; }
    public void setSourceRule(GainRule sourceRule) { this.sourceRule = sourceRule; }

    public Integer getWelcomePointsDefault() { return welcomePointsDefault; }
    public Integer getWelcomePointsMax() { return welcomePointsMax; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

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
        RestaurantGainRule that = (RestaurantGainRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
