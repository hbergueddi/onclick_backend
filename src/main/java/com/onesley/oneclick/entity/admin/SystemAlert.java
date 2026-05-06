package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.system_alerts} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "system_alerts")
@EntityListeners(AuditingEntityListener.class)
public class SystemAlert {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

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

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SystemAlert() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public String getMetricName() { return metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public BigDecimal getThreshold() { return threshold; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Boolean getAcknowledged() { return acknowledged; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
