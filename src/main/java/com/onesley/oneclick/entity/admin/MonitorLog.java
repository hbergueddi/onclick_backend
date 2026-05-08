package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.monitor_logs} — telemetry events ingérés via EF
 * {@code monitor-telemetry} (rétention 30j, dashboard monitor.app-oneclick.net).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code user_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (events système sans user contexte).</li>
 * </ul>
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

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Profile user;

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
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }
    public String getPlatform() { return platform; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getDurationMs() { return durationMs; }
    public String getIpAddress() { return ipAddress; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        MonitorLog that = (MonitorLog) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
