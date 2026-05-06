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
 * Entité {@code public.quota_change_logs} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "quota_change_logs")
@EntityListeners(AuditingEntityListener.class)
public class QuotaChangeLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "old_quota", nullable = false)
    private Integer oldQuota;

    @Column(name = "new_quota", nullable = false)
    private Integer newQuota;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_by_name", nullable = false)
    private String changedByName;

    @Column(name = "change_source", nullable = false)
    private String changeSource;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuotaChangeLog() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getServiceType() { return serviceType; }
    public String getServiceName() { return serviceName; }
    public Integer getOldQuota() { return oldQuota; }
    public Integer getNewQuota() { return newQuota; }
    public UUID getChangedBy() { return changedBy; }
    public String getChangedByName() { return changedByName; }
    public String getChangeSource() { return changeSource; }
    public Instant getCreatedAt() { return createdAt; }
}
