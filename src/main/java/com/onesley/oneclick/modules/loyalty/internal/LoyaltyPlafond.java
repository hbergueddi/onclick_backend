package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondDto;
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
 * Plafond / limite anti-abus du programme OneClick Lounge — géré par l'admin
 * (FORGE). Introduit par la migration V44. Ressource plateforme (pas de
 * tenant_id / restaurant_id ; le {@code scope} indique le niveau d'application).
 */
@Entity
@Table(name = "loyalty_plafonds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyPlafond extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    @Setter private String name;

    /** client | restaurant | global (CHECK DB). */
    @Column(name = "scope", nullable = false)
    @Setter private String scope = "global";

    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    @Setter private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "unit", nullable = false)
    @Setter private String unit = "points";

    @Column(name = "description")
    @Setter private String description;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public LoyaltyPlafond(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public LoyaltyPlafondDto toDto() {
        return new LoyaltyPlafondDto(
            id, name, scope, value, unit, description, enabled,
            getCreatedAt(), getUpdatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoyaltyPlafond that = (LoyaltyPlafond) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
