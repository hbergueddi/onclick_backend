package com.onesley.oneclick.core.audit_log.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity Sprint I.3 — ingestion télémétrie batch app mobile (port EF monitor-telemetry).
 *
 * <p>Table {@code monitor_logs} V22. Stocke les events comme push_register,
 * push_receive, token_register, app_startup, etc.
 */
@Entity
@Table(name = "monitor_logs")
@Getter
public class MonitorTelemetry {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id")
    @Setter private UUID userId;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "app_id", length = 64)
    @Setter private String appId;

    @Column(name = "event_type", nullable = false, length = 64)
    @Setter private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    @Setter private Map<String, Object> eventData;

    @Column(length = 32)
    @Setter private String platform;

    @Column(name = "app_version", length = 32)
    @Setter private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
