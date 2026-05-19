package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Rate limit AI par user/jour — Sprint H.
 *
 * <p>Géré au niveau loyalty (gain rules + ai = même domaine "user engagement").
 */
@Entity
@Table(name = "ai_usage")
@Getter
public class AIUsage extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Setter private UUID userId;

    @Column(name = "prompt_count", nullable = false)
    @Setter private Integer promptCount = 0;

    @Column(name = "last_prompt_at")
    @Setter private Instant lastPromptAt;
}
