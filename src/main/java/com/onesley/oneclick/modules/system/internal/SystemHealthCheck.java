package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "system_health_checks")
@Getter
public class SystemHealthCheck {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 64)
    @Setter private String component;

    @Column(nullable = false, length = 64)
    @Setter private String status;

    @Column(name = "latency_ms")
    @Setter private Integer latencyMs;

    @Column(name = "error_message", length = 1024)
    @Setter private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Setter private Map<String, Object> metadata;

    @Column(name = "checked_at", nullable = false)
    @Setter private Instant checkedAt = Instant.now();
}
