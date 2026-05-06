package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.system_health_checks} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "system_health_checks")
@EntityListeners(AuditingEntityListener.class)
public class SystemHealthCheck {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private BigDecimal metricValue;

    @Column(name = "metric_unit", nullable = false)
    private String metricUnit;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "details")
    private String details;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SystemHealthCheck() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getMetricName() { return metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public String getMetricUnit() { return metricUnit; }
    public String getStatus() { return status; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
