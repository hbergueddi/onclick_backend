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
 * Entité {@code public.ai_usage} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "ai_usage")
@EntityListeners(AuditingEntityListener.class)
public class AiUsage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "prompt_count", nullable = false)
    private Integer promptCount;

    @Column(name = "last_prompt_at")
    private Instant lastPromptAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsage() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Integer getPromptCount() { return promptCount; }
    public Instant getLastPromptAt() { return lastPromptAt; }
    public Instant getCreatedAt() { return createdAt; }
}
