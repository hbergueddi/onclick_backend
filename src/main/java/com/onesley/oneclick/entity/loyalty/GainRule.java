package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.gain_rules} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "gain_rules")
public class GainRule extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "max_points_par_ticket", nullable = false)
    private Integer maxPointsParTicket;

    @Column(name = "period_type", nullable = false)
    private String periodType;

    @Column(name = "period_value", nullable = false)
    private Integer periodValue;

    @Column(name = "benefit_duration_days", nullable = false)
    private Integer benefitDurationDays;

    @Column(name = "min_spend_monthly", nullable = false)
    private BigDecimal minSpendMonthly;

    @Column(name = "point_value_mad", nullable = false, precision = 6, scale = 2)
    private BigDecimal pointValueMad;

    @Column(name = "max_redemption_per_24h", nullable = false)
    private Integer maxRedemptionPer24h;

    @Column(name = "max_redemption_ratio_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxRedemptionRatioPct;

    @Column(name = "otp_required_above_pts", nullable = false)
    private Integer otpRequiredAbovePts;

    @Column(name = "otp_required_above_ratio_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal otpRequiredAboveRatioPct;

    @Column(name = "tenant_id")
    private UUID tenantId;

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
}
