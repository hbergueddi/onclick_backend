package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Rate limit AI par user/jour — Sprint H.
 *
 * <p>Géré au niveau loyalty (gain rules + ai = même domaine "user engagement").
 */
@Entity
@Table(name = "ai_usage")
public class AIUsage extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "prompt_count", nullable = false)
    private Integer promptCount = 0;

    @Column(name = "last_prompt_at")
    private Instant lastPromptAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public Integer getPromptCount() { return promptCount; }
    public void setPromptCount(Integer v) { this.promptCount = v; }
    public Instant getLastPromptAt() { return lastPromptAt; }
    public void setLastPromptAt(Instant v) { this.lastPromptAt = v; }
}
