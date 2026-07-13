package com.onesley.oneclick.core.ai.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Message de conversation persistant (table {@code ai_conversation_messages}) — module {@code core/ai}.
 * Append-only : créé via {@code JpaConversationMemory}, jamais modifié.
 */
@Entity
@Table(name = "ai_conversation_messages")
@Getter
public class AiConversationMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private String conversationId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, updatable = false)
    private String role;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiConversationMessage() {
        // requis par JPA
    }

    AiConversationMessage(UUID id, String conversationId, UUID userId, String role, String content, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}
