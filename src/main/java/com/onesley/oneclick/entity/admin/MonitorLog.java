package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.monitor_logs} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "monitor_logs")
@EntityListeners(AuditingEntityListener.class)
public class MonitorLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotBlank
    @Column(name = "source", nullable = false)
    private String source;

    @NotBlank
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "status")
    private String status;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "platform")
    private String platform;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "ip_address")
    private String ipAddress;

    protected MonitorLog() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSource() { return source; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public UUID getUserId() { return userId; }
    public String getPlatform() { return platform; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getDurationMs() { return durationMs; }
    public String getIpAddress() { return ipAddress; }
}
