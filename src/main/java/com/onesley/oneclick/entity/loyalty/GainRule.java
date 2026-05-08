package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.tenant.Tenant;
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
 * Entité {@code public.gain_rules} — règles globales de gain de points
 * (templates réutilisés/override par {@code restaurant_gain_rules}).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable
 *       (règles globales OneClick standard sans tenant_id, ou tenant-spécifiques).</li>
 * </ul>
 */
@Entity
@Table(name = "gain_rules")
public class GainRule extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @NotNull
    @Column(name = "max_points_par_ticket", nullable = false)
    private Integer maxPointsParTicket;

    @NotBlank
    @Column(name = "period_type", nullable = false)
    private String periodType;

    @NotNull
    @Column(name = "period_value", nullable = false)
    private Integer periodValue;

    @NotNull
    @Column(name = "benefit_duration_days", nullable = false)
    private Integer benefitDurationDays;

    @NotNull
    @Column(name = "min_spend_monthly", nullable = false)
    private BigDecimal minSpendMonthly;

    @Digits(integer = 4, fraction = 2)
    @NotNull
    @Column(name = "point_value_mad", nullable = false, precision = 6, scale = 2)
    private BigDecimal pointValueMad;

    @NotNull
    @Column(name = "max_redemption_per_24h", nullable = false)
    private Integer maxRedemptionPer24h;

    @Digits(integer = 3, fraction = 2)
    @NotNull
    @Column(name = "max_redemption_ratio_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxRedemptionRatioPct;

    @NotNull
    @Column(name = "otp_required_above_pts", nullable = false)
    private Integer otpRequiredAbovePts;

    @Digits(integer = 3, fraction = 2)
    @NotNull
    @Column(name = "otp_required_above_ratio_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal otpRequiredAboveRatioPct;

    // ─── Jointure tenant_id (Tenant, nullable) ──────────────────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    protected GainRule() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public BigDecimal getTauxConversion() { return tauxConversion; }
    public BigDecimal getMinTicket() { return minTicket; }
    public Boolean getEnabled() { return enabled; }
    public Integer getMaxPointsParTicket() { return maxPointsParTicket; }
    public String getPeriodType() { return periodType; }
    public Integer getPeriodValue() { return periodValue; }
    public Integer getBenefitDurationDays() { return benefitDurationDays; }
    public BigDecimal getMinSpendMonthly() { return minSpendMonthly; }
    public BigDecimal getPointValueMad() { return pointValueMad; }
    public Integer getMaxRedemptionPer24h() { return maxRedemptionPer24h; }
    public BigDecimal getMaxRedemptionRatioPct() { return maxRedemptionRatioPct; }
    public Integer getOtpRequiredAbovePts() { return otpRequiredAbovePts; }
    public BigDecimal getOtpRequiredAboveRatioPct() { return otpRequiredAboveRatioPct; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

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
        GainRule that = (GainRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
