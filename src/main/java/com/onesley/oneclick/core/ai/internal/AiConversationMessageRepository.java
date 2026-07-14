package com.onesley.oneclick.core.ai.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository des messages de conversation ({@link AiConversationMessage}) — module {@code core/ai}.
 */
interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, UUID> {

    /**
     * Derniers messages d'une conversation <b>appartenant à l'utilisateur donné</b> (plus récents d'abord —
     * l'appelant réordonne). Le scoping par {@code userId} empêche la lecture de l'historique d'autrui via un
     * {@code conversationId} deviné/repris. Le cas {@code userId == null} (mode dev sans JWT) ne matche que les
     * tours eux-mêmes non attribués.
     */
    @Query("""
        SELECT m FROM AiConversationMessage m
        WHERE m.conversationId = :conversationId
          AND ((:userId IS NULL AND m.userId IS NULL) OR m.userId = :userId)
        ORDER BY m.createdAt DESC
        """)
    List<AiConversationMessage> findRecentForUser(@Param("conversationId") String conversationId,
                                                  @Param("userId") UUID userId,
                                                  Pageable pageable);
}
