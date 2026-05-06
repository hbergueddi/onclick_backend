package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.admin_wallet_transactions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "admin_wallet_transactions")
@EntityListeners(AuditingEntityListener.class)
public class AdminWalletTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "details")
    private String details;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "remaining_amount", nullable = false)
    private Integer remainingAmount;

    protected AdminWalletTransaction() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public UUID getRestaurantId() { return restaurantId; }
    public Integer getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getRemainingAmount() { return remainingAmount; }
}
