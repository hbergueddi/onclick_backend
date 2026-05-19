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
@Table(name = "system_alerts")
@Getter
public class SystemAlert {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "rule_id")
    @Setter private UUID ruleId;

    @Column(nullable = false, length = 32)
    @Setter private String severity;

    @Column(nullable = false)
    @Setter private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Setter private Map<String, Object> context;

    @Column(name = "acknowledged_at")
    @Setter private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    @Setter private UUID acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
