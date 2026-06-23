package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Règle de gain de points PAR restaurant — override des {@link LoyaltyRule} globaux.
 *
 * <p>Modèle introduit par la migration V13 (Sprint B) :
 * <ul>
 * <li>{@code conversion_rate} — taux MAD → points (0..1)</li>
 * <li>{@code cap_per_visit} — plafond points par visite (optionnel)</li>
 * <li>{@code cap_per_month} — plafond points par mois (optionnel)</li>
 * <li>{@code min_amount} — montant minimum ticket éligible (optionnel)</li>
 * </ul>
 *
 * <p>{@code tenant_id} est rempli par trigger DB depuis {@code restaurants.tenant_id}
 * (cf. {@code fill_tenant_id_from_restaurant_for_gain_rules}) → mapping read-only
 * côté Hibernate (insertable=false, updatable=false).
 *
 * <p>Contrainte UNIQUE sur {@code restaurant_id} : 1 règle active par restaurant.
 */
@Entity
@Table(
    name = "gain_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GainRule extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    // tenant_id rempli automatiquement par trigger DB V13 (depuis restaurants.tenant_id)
    // → read-only côté Hibernate
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "conversion_rate", nullable = false, precision = 6, scale = 4)
    @Setter private BigDecimal conversionRate = new BigDecimal("0.1000");

    @Column(name = "cap_per_visit")
    @Setter private Integer capPerVisit;

    @Column(name = "cap_per_month")
    @Setter private Integer capPerMonth;

    @Column(name = "min_amount", precision = 10, scale = 2)
    @Setter private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Setter private boolean isActive = true;

    /**
     * Bonus de bienvenue par défaut crédité à l'inscription d'un nouveau membre
     * (cf. V28 + EnrollmentService). Pré-rempli dans le formulaire EnrollMember.
     */
    
    @Column(name = "welcome_points_default", nullable = false)
    @Setter private int welcomePointsDefault = 100;

    /**
     * Plafond du bonus de bienvenue. EnrollmentService rejette toute demande
     * &gt; welcomePointsMax (anti-abus staff). DB CHECK garantit max &gt;= default.
     */
    
    @Column(name = "welcome_points_max", nullable = false)
    @Setter private int welcomePointsMax = 500;

    /**
     * Tier-rule plateforme source si cette règle a été assignée en masse depuis
     * la FORGE (Gap #1). NULL = règle par-restaurant créée/éditée manuellement.
     * Permet la traçabilité + le resync (cf. {@code RuleAssignmentService}).
     */
    @Column(name = "source_tier_rule_id")
    @Setter private UUID sourceTierRuleId;

    /**
     * Seuil (points) au-delà duquel une conversion (spend) requiert un OTP validé
     * par le client (Gap #2). Anti-abus staff sur les grosses conversions.
     */
    @Column(name = "otp_required_above_pts", nullable = false)
    @Setter private int otpRequiredAbovePts = 200;

    /**
     * Seuil (% du ticket) au-delà duquel un OTP est requis. Conservé pour parité
     * legacy ; en Spring le déclencheur actif est {@code otpRequiredAbovePts}
     * (conversion dénominée en points, pas en MAD).
     */
    @Column(name = "otp_required_above_ratio_pct", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal otpRequiredAboveRatioPct = new BigDecimal("50.00");

    // ─── Lot 4b — champs « RuleBuilder » legacy (V107, additifs nullable) ────────
    /** Override de la valeur du point (MAD) configuré dans la règle ; NULL → valeur résolue loyalty_rules. */
    @Column(name = "point_value_mad", precision = 10, scale = 4)
    @Setter private BigDecimal pointValueMad;

    /** Type de période d'évaluation RuleBuilder : {@code week} | {@code month} (nullable). */
    @Column(name = "eval_period_type")
    @Setter private String evalPeriodType;

    /** Nombre de périodes d'évaluation (nullable). */
    @Column(name = "eval_period_value")
    @Setter private Integer evalPeriodValue;

    /** Durée du bénéfice en jours (nullable). */
    @Column(name = "benefit_duration_days")
    @Setter private Integer benefitDurationDays;

    /** Seuil de dépense mensuel d'activation (MAD, nullable). */
    @Column(name = "min_spend_monthly", precision = 12, scale = 2)
    @Setter private BigDecimal minSpendMonthly;

    /**
     * Valeur par défaut d'un point (1 pt = 1 MAD) quand aucune {@code loyalty_rules}
     * n'est configurée pour le restaurant — reflète la réalité actuelle.
     * Source de vérité réelle = {@code loyalty_rules.point_value} (DEFAULT 1.0000).
     */
    public static final BigDecimal DEFAULT_POINT_VALUE_MAD = new BigDecimal("1.0000");

    public GainRule(UUID id, UUID restaurantId, BigDecimal conversionRate) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.conversionRate = conversionRate;
    }

    /**
     * Mapping vers le DTO public, avec {@code pointValueMad} par défaut
     * ({@link #DEFAULT_POINT_VALUE_MAD}). Utilisé sur les chemins d'écriture
     * (create/patch/assign) où la valeur du point n'est pas résolue : elle reste
     * 1 pt = 1 MAD tant qu'aucune {@code loyalty_rules} n'est configurée.
     */
    public GainRuleDto toDto() {
        return toDto(DEFAULT_POINT_VALUE_MAD);
    }

    /**
     * Mapping vers le DTO public en injectant la valeur du point résolue depuis
     * {@code loyalty_rules} (source de vérité, hors table {@code gain_rules}).
     *
     * @param pointValueMad valeur monétaire d'un point ; {@code null} → défaut 1.0
     */
    public GainRuleDto toDto(BigDecimal resolvedPointValueMad) {
        // L'override de la règle (V107) prime sur la valeur résolue depuis loyalty_rules ; sinon défaut.
        BigDecimal effectivePointValue = this.pointValueMad != null
            ? this.pointValueMad
            : (resolvedPointValueMad != null ? resolvedPointValueMad : DEFAULT_POINT_VALUE_MAD);
        return new GainRuleDto(
            id, restaurantId, conversionRate,
            capPerVisit, capPerMonth, minAmount,
            isActive, welcomePointsDefault, welcomePointsMax, getCreatedAt(),
            effectivePointValue,
            evalPeriodType, evalPeriodValue, benefitDurationDays, minSpendMonthly
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        GainRule that = (GainRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
