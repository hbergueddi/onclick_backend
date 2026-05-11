package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Niveau de fidélité (Ruby, Sapphire, Emeraude, ...) par tenant.
 */
@Entity
@Table(
    name = "tiers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"})
)
public class Tier extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Min(0)
    @Column(name = "min_points", nullable = false)
    private Integer minPoints = 0;

    @DecimalMin("0.00")
    @Column(name = "bonus_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal bonusPercent = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    protected Tier() {
        // JPA
    }

    public Tier(UUID id, Tenant tenant, String name, Integer minPoints, BigDecimal bonusPercent) {
        this.id = id;
        this.tenant = tenant;
        this.name = name;
        this.minPoints = minPoints;
        this.bonusPercent = bonusPercent;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public String getName() { return name; }
    public Integer getMinPoints() { return minPoints; }
    public BigDecimal getBonusPercent() { return bonusPercent; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Tier that = (Tier) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
