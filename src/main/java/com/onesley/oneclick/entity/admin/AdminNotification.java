package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.admin_notifications} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "admin_notifications")
@EntityListeners(AuditingEntityListener.class)
public class AdminNotification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "target_url")
    private String targetUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    protected AdminNotification() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getAdminId() { return adminId; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTargetUrl() { return targetUrl; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getReadAt() { return readAt; }
    public Instant getDismissedAt() { return dismissedAt; }
}
