package com.onesley.oneclick.entity.misc;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.loyalty_plafonds} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "loyalty_plafonds")
public class LoyaltyPlafond extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    protected LoyaltyPlafond() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getValue() { return value; }
    public String getUnit() { return unit; }
    public String getScope() { return scope; }
    public Boolean getEnabled() { return enabled; }
}
