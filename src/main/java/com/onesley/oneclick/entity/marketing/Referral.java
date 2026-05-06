package com.onesley.oneclick.entity.marketing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.referrals} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "referrals")
@EntityListeners(AuditingEntityListener.class)
public class Referral {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "referrer_id", nullable = false)
    private UUID referrerId;

    @NotBlank
    @Column(name = "referred_phone", nullable = false)
    private String referredPhone;

    @Column(name = "referred_name")
    private String referredName;

    @Column(name = "referred_user_id")
    private UUID referredUserId;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @NotNull
    @Column(name = "pts_awarded", nullable = false)
    private Integer ptsAwarded;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected Referral() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getReferrerId() { return referrerId; }
    public String getReferredPhone() { return referredPhone; }
    public String getReferredName() { return referredName; }
    public UUID getReferredUserId() { return referredUserId; }
    public String getStatus() { return status; }
    public Integer getPtsAwarded() { return ptsAwarded; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }
}
