package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @NotBlank
    @Column(name = "operator", nullable = false)
    private String operator;

    @NotNull
    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @NotBlank
    @Column(name = "severity", nullable = false)
    private String severity;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @NotNull
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
