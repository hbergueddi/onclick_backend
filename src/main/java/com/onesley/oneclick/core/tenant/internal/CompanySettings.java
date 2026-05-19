package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.Tenant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration légale et facturation par tenant (raison sociale, ICE, RIB, TVA).
 * Multi-tenant 1-1 avec {@link Tenant}.
 */
@Entity
@Table(name = "company_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySettings extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false, unique = true)
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @NotBlank
    @Column(name = "raison_sociale", nullable = false)
    @Setter private String raisonSociale;

    @Column(name = "ice")
    @Setter private String ice;

    @Column(name = "rib")
    @Setter private String rib;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "tva_rate", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal tvaRate = new BigDecimal("20.00");

    public CompanySettings(UUID id, Tenant tenant, String raisonSociale) {
        this.id = id;
        this.tenant = tenant;
        this.raisonSociale = raisonSociale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        CompanySettings that = (CompanySettings) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
