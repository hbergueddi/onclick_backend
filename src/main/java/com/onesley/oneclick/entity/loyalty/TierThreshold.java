package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.tier_thresholds} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "tier_thresholds")
public class TierThreshold extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tier_name", nullable = false)
    private String tierName;

    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Column(name = "min_spend", nullable = false)
    private BigDecimal minSpend;

    @Column(name = "points_required", nullable = false)
    private Integer pointsRequired;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "benefits")
    private String benefits;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "gain_bonus_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal gainBonusPct;

    @Column(name = "point_value_mad_override", precision = 6, scale = 2)
    private BigDecimal pointValueMadOverride;

    @Column(name = "min_ticket_override")
    private BigDecimal minTicketOverride;

    @Column(name = "max_points_per_ticket_override")
    private Integer maxPointsPerTicketOverride;

    @Column(name = "benefit_duration_days_override")
    private Integer benefitDurationDaysOverride;

    @Column(name = "max_redemption_per_24h_override")
    private Integer maxRedemptionPer24hOverride;

    @Column(name = "max_redemption_ratio_pct_override", precision = 5, scale = 2)
    private BigDecimal maxRedemptionRatioPctOverride;

    @Column(name = "otp_required_above_pts_override")
    private Integer otpRequiredAbovePtsOverride;

    @Column(name = "otp_required_above_ratio_pct_override", precision = 5, scale = 2)
    private BigDecimal otpRequiredAboveRatioPctOverride;

    @Column(name = "taux_conversion_override", precision = 5, scale = 4)
    private BigDecimal tauxConversionOverride;

    protected TierThreshold() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getTierName() { return tierName; }
    public Integer getPeriodDays() { return periodDays; }
    public BigDecimal getMinSpend() { return minSpend; }
    public Integer getPointsRequired() { return pointsRequired; }
    public Integer getSortOrder() { return sortOrder; }
    public String getBenefits() { return benefits; }
    public Boolean getEnabled() { return enabled; }
    public BigDecimal getGainBonusPct() { return gainBonusPct; }
    public BigDecimal getPointValueMadOverride() { return pointValueMadOverride; }
    public BigDecimal getMinTicketOverride() { return minTicketOverride; }
    public Integer getMaxPointsPerTicketOverride() { return maxPointsPerTicketOverride; }
    public Integer getBenefitDurationDaysOverride() { return benefitDurationDaysOverride; }
    public Integer getMaxRedemptionPer24hOverride() { return maxRedemptionPer24hOverride; }
    public BigDecimal getMaxRedemptionRatioPctOverride() { return maxRedemptionRatioPctOverride; }
    public Integer getOtpRequiredAbovePtsOverride() { return otpRequiredAbovePtsOverride; }
    public BigDecimal getOtpRequiredAboveRatioPctOverride() { return otpRequiredAboveRatioPctOverride; }
    public BigDecimal getTauxConversionOverride() { return tauxConversionOverride; }
}
