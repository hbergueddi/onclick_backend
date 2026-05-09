package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.entity.status.EntityStatus;
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
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.system_health_checks} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "system_health_checks")
public class SystemHealthCheck extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @NotNull
    @Column(name = "metric_value", nullable = false)
    private BigDecimal metricValue;

    @NotBlank
    @Column(name = "metric_unit", nullable = false)
    private String metricUnit;

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @Column(name = "details")
    private String details;

    protected SystemHealthCheck() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getMetricName() { return metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public String getMetricUnit() { return metricUnit; }
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public String getDetails() { return details; }
}
