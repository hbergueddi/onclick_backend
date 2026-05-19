package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank @Pattern(regexp = "^(pending|approved|rejected)$") private String status = "pending";

    @Column @Setter private String motivation;

    @Column(name = "referrer_id")
    @Setter private UUID referrerId;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "rejection_reason")
    @Setter @Size(max = 2000) private String rejectionReason;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
