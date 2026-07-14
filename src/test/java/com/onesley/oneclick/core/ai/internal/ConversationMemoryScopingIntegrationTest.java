package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.ai.api.ConversationMemory;
import com.onesley.oneclick.core.ai.api.ConversationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la propriété de sécurité de la mémoire de conversation : un utilisateur ne peut relire
 * l'historique d'un autre en fournissant un {@code conversationId} qui ne lui appartient pas.
 *
 * <p>Test DB-backed (vraie requête JPQL {@code findRecentForUser}) : le scoping vit dans le SQL,
 * un test à base de mocks ne l'exercerait pas. On écrit des tours pour deux utilisateurs distincts
 * sous un même {@code conversationId}, puis on asserte que {@link ConversationMemory#recent} ne
 * renvoie que les messages de l'appelant.
 */
class ConversationMemoryScopingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationMemory memory;

    /** conversationId partagé par les deux utilisateurs — unique par exécution, nettoyé en @AfterEach. */
    private final String conversationId = "test-conv-" + UUID.randomUUID();

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM ai_conversation_messages WHERE conversation_id = ?", conversationId);
    }

    @Test
    void recent_returnsOnlyCallerMessages_notAnotherUsersHistory() {
        memory.append(conversationId, userA, ConversationMessage.ROLE_USER, "question de A");
        memory.append(conversationId, userA, ConversationMessage.ROLE_ASSISTANT, "réponse à A");
        memory.append(conversationId, userB, ConversationMessage.ROLE_USER, "secret de B");

        // A ne voit que ses deux tours, en ordre chronologique (user puis assistant).
        List<ConversationMessage> forA = memory.recent(conversationId, userA, 10);
        assertThat(forA).extracting(ConversationMessage::content)
            .containsExactly("question de A", "réponse à A")
            .doesNotContain("secret de B");

        // B ne voit que son propre message, jamais celui de A.
        List<ConversationMessage> forB = memory.recent(conversationId, userB, 10);
        assertThat(forB).extracting(ConversationMessage::content)
            .containsExactly("secret de B");
    }

    @Test
    void recent_withForeignConversationId_returnsEmpty() {
        memory.append(conversationId, userA, ConversationMessage.ROLE_USER, "question de A");

        // Un tiers qui devine/reprend le conversationId de A n'obtient rien.
        UUID intruder = UUID.randomUUID();
        assertThat(memory.recent(conversationId, intruder, 10)).isEmpty();
    }
}
