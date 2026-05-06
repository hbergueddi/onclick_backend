package com.onesley.oneclick.entity.misc;

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

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @NotBlank
    @Column(name = "unit", nullable = false)
    private String unit;

    @NotBlank
    @Column(name = "scope", nullable = false)
    private String scope;

    @NotNull
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
