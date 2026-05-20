package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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
    @Setter private String quotaType;

    @Column(name = "old_value")
    @Setter private Integer oldValue;

    @Column(name = "new_value")
    @Setter private Integer newValue;

    @Column(length = 1024) @Setter private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
     private Instant createdAt = Instant.now();
}
