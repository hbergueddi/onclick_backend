package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.email_bounces} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "email_bounces")
public class EmailBounce extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "bounce_type", nullable = false)
    private String bounceType;

    @Column(name = "bounce_reason")
    private String bounceReason;

    @Column(name = "is_suppressed", nullable = false)
    private Boolean isSuppressed;

    @Column(name = "last_bounced_at", nullable = false)
    private Instant lastBouncedAt;

    @Column(name = "bounce_count", nullable = false)
    private Integer bounceCount;

    @Column(name = "source_ef")
    private String sourceEf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_event", columnDefinition = "jsonb")
    private Map<String, Object> rawEvent = new HashMap<>();

    protected EmailBounce() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getBounceType() { return bounceType; }
    public String getBounceReason() { return bounceReason; }
    public Boolean getIsSuppressed() { return isSuppressed; }
    public Instant getLastBouncedAt() { return lastBouncedAt; }
    public Integer getBounceCount() { return bounceCount; }
    public String getSourceEf() { return sourceEf; }
    public Map<String, Object> getRawEvent() { return rawEvent; }
}
