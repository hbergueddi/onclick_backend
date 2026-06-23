package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // ─── Champs enrollment legacy (V58) — collectés par le formulaire public ──
    @Column(length = 8) @Setter private String budget;
    @Column(length = 2000) @Setter private String description;
    @Column(name = "owner_role", length = 64) @Setter private String ownerRole;
    @Column(length = 32) @Setter private String ice;
    @Column(name = "if_number", length = 32) @Setter private String ifNumber;
    @Column(length = 64) @Setter private String rc;
    @Column(length = 64) @Setter private String patente;
    @Column @Setter private Integer capacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "services", columnDefinition = "text[]")
    @Setter private String[] services;

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

    // BE-2 — entités créées à l'approbation (provisioning) : ancre d'idempotence + audit.
    @Column(name = "provisioned_user_id")
    @Setter private UUID provisionedUserId;

    @Column(name = "provisioned_restaurant_id")
    @Setter private UUID provisionedRestaurantId;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
