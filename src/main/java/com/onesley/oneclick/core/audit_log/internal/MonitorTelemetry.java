package com.onesley.oneclick.core.audit_log.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity Sprint I.3 — ingestion télémétrie batch app mobile (port EF monitor-telemetry).
 *
 * <p>Table {@code monitor_logs} V22. Stocke les events comme push_register,
 * push_receive, token_register, app_startup, etc.
 */
@Entity
@Table(name = "monitor_logs")
public class MonitorTelemetry {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "app_id", length = 64)
    private String appId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @Column(length = 32)
    private String platform;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getAppId() { return appId; }
    public void setAppId(String v) { this.appId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public Map<String, Object> getEventData() { return eventData; }
    public void setEventData(Map<String, Object> v) { this.eventData = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { this.platform = v; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String v) { this.appVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
}
