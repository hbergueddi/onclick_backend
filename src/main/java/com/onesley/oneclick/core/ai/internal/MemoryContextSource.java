package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.ConversationMemory;
import com.onesley.oneclick.core.ai.api.ConversationMessage;
import com.onesley.oneclick.core.ai.api.ContextSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Source de contexte « mémoire de conversation » — module {@code core/ai}.
 *
 * <p>Implémente le port {@link ContextSource} (name {@code "memory"}) : l'orchestrateur la combine avec
 * les autres sources sans la connaître spécifiquement. Fournit les derniers tours de la conversation
 * (via {@link ConversationMemory}) sous forme de {@link ContextFragment}. N'apporte rien si la requête
 * n'a pas de {@code conversationId}.
 */
@Component
@RequiredArgsConstructor
class MemoryContextSource implements ContextSource {

    /** Nombre de tours récents injectés dans le contexte. */
    private static final int HISTORY_LIMIT = 10;

    private final ConversationMemory memory;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public List<ContextFragment> retrieve(AiQuery query) {
        String conversationId = query.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return memory.recent(conversationId, HISTORY_LIMIT).stream()
            .map(m -> new ContextFragment(
                label(m.role()) + " : " + m.content(),
                Map.of("kind", "conversation", "role", m.role()),
                null))
            .toList();
    }

    private static String label(String role) {
        return ConversationMessage.ROLE_ASSISTANT.equals(role) ? "Assistant" : "Utilisateur";
    }
}
