package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "restaurant_name", nullable = false, length = 128)
    @Setter private String restaurantName;

    @Column(length = 64) @Setter private String cuisine;
    @Column(length = 128) @Setter private String city;
    @Column(length = 256) @Setter private String address;
    @Column(length = 64) @Setter private String phone;

    @Column(name = "owner_first_name", nullable = false, length = 128)
    @Setter private String ownerFirstName;

    @Column(name = "owner_last_name", nullable = false, length = 128)
    @Setter private String ownerLastName;

    @Column(name = "owner_email", nullable = false, length = 256)
    @Setter private String ownerEmail;

    @Column(name = "owner_phone", length = 64)
    @Setter private String ownerPhone;

    @Column(nullable = false, length = 64)
    @Setter private String status = "pending";

    @Column(name = "rejection_reason", length = 1024)
    @Setter private String rejectionReason;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "decision_email_sent_at")
    @Setter private Instant decisionEmailSentAt;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
