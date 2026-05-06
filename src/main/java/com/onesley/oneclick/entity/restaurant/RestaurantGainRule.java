package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_gain_rules} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_gain_rules")
public class RestaurantGainRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "taux_conversion", nullable = false)
    private BigDecimal tauxConversion;

    @Column(name = "min_ticket", nullable = false)
    private BigDecimal minTicket;

    @Column(name = "max_points_par_ticket", nullable = false)
    private Integer maxPointsParTicket;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "point_value_mad", precision = 6, scale = 2)
    private BigDecimal pointValueMad;

    @Column(name = "source_rule_id")
    private UUID sourceRuleId;

    @Column(name = "welcome_points_default", nullable = false)
    private Integer welcomePointsDefault;

    @Column(name = "welcome_points_max", nullable = false)
    private Integer welcomePointsMax;

    protected RestaurantGainRule() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public BigDecimal getTauxConversion() { return tauxConversion; }
    public BigDecimal getMinTicket() { return minTicket; }
    public Integer getMaxPointsParTicket() { return maxPointsParTicket; }
    public Boolean getEnabled() { return enabled; }
    public BigDecimal getPointValueMad() { return pointValueMad; }
    public UUID getSourceRuleId() { return sourceRuleId; }
    public Integer getWelcomePointsDefault() { return welcomePointsDefault; }
    public Integer getWelcomePointsMax() { return welcomePointsMax; }
}
