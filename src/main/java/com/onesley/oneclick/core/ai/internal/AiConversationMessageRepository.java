package com.onesley.oneclick.core.ai.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository des messages de conversation ({@link AiConversationMessage}) — module {@code core/ai}.
 */
interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, UUID> {

    /** Derniers messages d'une conversation (plus récents d'abord — l'appelant réordonne). */
    List<AiConversationMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
}
