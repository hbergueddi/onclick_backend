package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "quota_change_logs")
@Getter
public class QuotaChangeLog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_id")
    @Setter private UUID restaurantId;

    @Column(name = "user_id")
    @Setter private UUID userId;

    @Column(name = "quota_type", nullable = false, length = 64)
    @Setter @Size(max = 64) @NotBlank private String quotaType;

    @Column(name = "old_value")
    @Setter private Integer oldValue;

    @Column(name = "new_value")
    @Setter private Integer newValue;

    @Column @Setter private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull private Instant createdAt = Instant.now();
}
