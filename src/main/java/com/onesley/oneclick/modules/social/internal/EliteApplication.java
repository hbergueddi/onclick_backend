package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "elite_applications")
@Getter
public class EliteApplication extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    @Setter private UUID userId;

    @Column(nullable = false, length = 64)
    @Setter private String status = "pending";

    @Column(length = 1024) @Setter private String motivation;

    @Column(name = "referrer_id")
    @Setter private UUID referrerId;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 1024)
    @Setter private String rejectionReason;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
