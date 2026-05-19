package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
    @Setter @Size(max = 32) @NotBlank private String severity;

    @Column(nullable = false)
    @Setter @Size(max = 2000) @NotBlank private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Setter private Map<String, Object> context;

    @Column(name = "acknowledged_at")
    @Setter private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    @Setter private UUID acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull private Instant createdAt = Instant.now();
}
