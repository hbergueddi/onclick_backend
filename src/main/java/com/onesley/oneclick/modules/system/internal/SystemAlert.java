package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_alerts")
public class SystemAlert {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(nullable = false, length = 32)
    private String severity;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID v) { this.ruleId = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> v) { this.context = v; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant v) { this.acknowledgedAt = v; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID v) { this.acknowledgedBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
