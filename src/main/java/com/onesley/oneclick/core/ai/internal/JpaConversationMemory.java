package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.ConversationMemory;
import com.onesley.oneclick.core.ai.api.ConversationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation DB (JPA) du port {@link ConversationMemory} — module {@code core/ai}.
 *
 * <p>Persiste les tours de conversation dans {@code ai_conversation_messages}. Aucun type JPA ne fuit
 * hors de cette classe (le port ne manipule que des {@link ConversationMessage}). Remplacer par Redis
 * = fournir une autre implémentation du port, sans toucher au domaine.
 */
@Repository
@RequiredArgsConstructor
class JpaConversationMemory implements ConversationMemory {

    private final AiConversationMessageRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessage> recent(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        int capped = Math.min(Math.max(limit, 1), 50);
        List<AiConversationMessage> desc = repository.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, capped));
        // Réordonne en chronologique (plus ancien d'abord) pour la construction du prompt.
        return desc.stream()
            .sorted(java.util.Comparator.comparing(AiConversationMessage::getCreatedAt))
            .map(m -> new ConversationMessage(m.getRole(), m.getContent(), m.getCreatedAt()))
            .toList();
    }

    @Override
    @Transactional
    public void append(String conversationId, UUID userId, String role, String content) {
        if (conversationId == null || conversationId.isBlank() || content == null || content.isBlank()) {
            return;
        }
        repository.save(new AiConversationMessage(
            UUID.randomUUID(), conversationId, userId, role, content, Instant.now()));
    }
}
