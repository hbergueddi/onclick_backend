package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.loyalty.api.TierDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Niveau de fidélité (Ruby, Sapphire, Emeraude, ...) par tenant.
 */
@Entity
@Table(
    name = "tiers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tier extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

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
    @Setter private Integer sortOrder = 0;

    public Tier(UUID id, UUID tenantId, String name, Integer minPoints, BigDecimal bonusPercent) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.minPoints = minPoints;
        this.bonusPercent = bonusPercent;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public TierDto toDto() {
        return new TierDto(id, tenantId, name, minPoints, bonusPercent, sortOrder);
    }

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
