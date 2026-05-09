package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.system_alerts} — alertes système déclenchées
 * (cf. {@link SystemAlertRule} → trigger → SystemAlert).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code rule_id} → {@link SystemAlertRule} en {@code @ManyToOne(LAZY)}, nullable
 *       (alertes legacy sans rule association).</li>
 *   <li>{@code acknowledged_by} : audit field, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "system_alerts")
public class SystemAlert extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_id", insertable = false, updatable = false)
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private SystemAlertRule rule;

    @NotBlank
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @NotNull
    @Column(name = "metric_value", nullable = false)
    private BigDecimal metricValue;

    @NotNull
    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @NotBlank
    @Column(name = "severity", nullable = false)
    private String severity;

    @NotBlank
    @Column(name = "message", nullable = false)
    private String message;

    @NotNull
    @Column(name = "acknowledged", nullable = false)
    private Boolean acknowledged;

    /** Audit field : UUID brut. */
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected SystemAlert() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public SystemAlertRule getRule() { return rule; }
    public void setRule(SystemAlertRule rule) { this.rule = rule; }
    public String getMetricName() { return metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public BigDecimal getThreshold() { return threshold; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Boolean getAcknowledged() { return acknowledged; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }

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
        SystemAlert that = (SystemAlert) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
