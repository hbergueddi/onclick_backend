package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "store_onboarding_requests")
@Getter
public class StoreOnboardingRequest extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_name", nullable = false, length = 255)
    @Setter @Size(max = 255) @NotBlank private String restaurantName;

    @Column @Setter private String cuisine;
    @Column @Setter private String city;
    @Column @Setter private String address;
    @Column @Setter private String phone;

    @Column(name = "owner_first_name", nullable = false, length = 128)
    @Setter @Size(max = 128) @NotBlank private String ownerFirstName;

    @Column(name = "owner_last_name", nullable = false, length = 128)
    @Setter @Size(max = 128) @NotBlank private String ownerLastName;

    @Column(name = "owner_email", nullable = false, length = 255)
    @Setter @Size(max = 255) @NotBlank private String ownerEmail;

    @Column(name = "owner_phone", length = 32)
    @Setter @Size(max = 32) private String ownerPhone;

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank @Pattern(regexp = "^(pending|approved|rejected|onboarded)$") private String status = "pending";

    @Column(name = "rejection_reason")
    @Setter @Size(max = 2000) private String rejectionReason;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "decision_email_sent_at")
    @Setter private Instant decisionEmailSentAt;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
