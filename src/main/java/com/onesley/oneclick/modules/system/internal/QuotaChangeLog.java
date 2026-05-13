package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quota_change_logs")
public class QuotaChangeLog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "quota_type", nullable = false, length = 64)
    private String quotaType;

    @Column(name = "old_value")
    private Integer oldValue;

    @Column(name = "new_value")
    private Integer newValue;

    @Column private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID v) { this.restaurantId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getQuotaType() { return quotaType; }
    public void setQuotaType(String v) { this.quotaType = v; }
    public Integer getOldValue() { return oldValue; }
    public void setOldValue(Integer v) { this.oldValue = v; }
    public Integer getNewValue() { return newValue; }
    public void setNewValue(Integer v) { this.newValue = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public Instant getCreatedAt() { return createdAt; }
}
