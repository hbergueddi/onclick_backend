package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_tier_config} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_tier_config")
public class RestaurantTierConfig extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "slug", nullable = false)
    private String slug;

    @NotNull
    @Column(name = "min_ca", nullable = false)
    private BigDecimal minCa;

    @Column(name = "max_ca")
    private BigDecimal maxCa;

    @NotNull
    @Column(name = "grace_period_months", nullable = false)
    private Integer gracePeriodMonths;

    @NotBlank
    @Column(name = "color", nullable = false)
    private String color;

    @NotBlank
    @Column(name = "icon", nullable = false)
    private String icon;

    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "description")
    private String description;

    protected RestaurantTierConfig() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public BigDecimal getMinCa() { return minCa; }
    public BigDecimal getMaxCa() { return maxCa; }
    public Integer getGracePeriodMonths() { return gracePeriodMonths; }
    public String getColor() { return color; }
    public String getIcon() { return icon; }
    public Integer getPosition() { return position; }
    public String getDescription() { return description; }
}
