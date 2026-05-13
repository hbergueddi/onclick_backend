package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_onboarding_requests")
public class StoreOnboardingRequest extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column private String cuisine;
    @Column private String city;
    @Column private String address;
    @Column private String phone;

    @Column(name = "owner_first_name", nullable = false)
    private String ownerFirstName;

    @Column(name = "owner_last_name", nullable = false)
    private String ownerLastName;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "owner_phone")
    private String ownerPhone;

    @Column(nullable = false, length = 32)
    private String status = "pending";

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "decision_email_sent_at")
    private Instant decisionEmailSentAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String v) { this.restaurantName = v; }
    public String getCuisine() { return cuisine; }
    public void setCuisine(String v) { this.cuisine = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getOwnerFirstName() { return ownerFirstName; }
    public void setOwnerFirstName(String v) { this.ownerFirstName = v; }
    public String getOwnerLastName() { return ownerLastName; }
    public void setOwnerLastName(String v) { this.ownerLastName = v; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String v) { this.ownerEmail = v; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String v) { this.ownerPhone = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String v) { this.rejectionReason = v; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID v) { this.reviewedBy = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public Instant getDecisionEmailSentAt() { return decisionEmailSentAt; }
    public void setDecisionEmailSentAt(Instant v) { this.decisionEmailSentAt = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
}
