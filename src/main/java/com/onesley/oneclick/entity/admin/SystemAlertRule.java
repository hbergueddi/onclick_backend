package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.system_alert_rules} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "system_alert_rules")
public class SystemAlertRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "operator", nullable = false)
    private String operator;

    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    protected SystemAlertRule() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getMetricName() { return metricName; }
    public String getOperator() { return operator; }
    public BigDecimal getThreshold() { return threshold; }
    public String getSeverity() { return severity; }
    public Boolean getEnabled() { return enabled; }
    public Integer getCooldownMinutes() { return cooldownMinutes; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
}
