package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_health_checks")
public class SystemHealthCheck {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 64)
    private String component;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt = Instant.now();

    public UUID getId() { return id; }
    public String getComponent() { return component; }
    public void setComponent(String v) { this.component = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer v) { this.latencyMs = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant v) { this.checkedAt = v; }
}
