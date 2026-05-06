package com.onesley.oneclick.entity.loyalty;

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
 * Entité {@code public.ai_usage_bypass} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "ai_usage_bypass")
@EntityListeners(AuditingEntityListener.class)
public class AiUsageBypass {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reason")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsageBypass() {
        // JPA
    }

    public UUID getUserId() { return userId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
