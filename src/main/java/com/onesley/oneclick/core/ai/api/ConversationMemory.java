package com.onesley.oneclick.core.ai.api;

import java.util.List;
import java.util.UUID;

/**
 * Port de <b>mémoire de conversation</b> — domaine {@code core/ai}.
 *
 * <p>Persiste et relit les tours d'une conversation (identifiée par {@code conversationId}) pour donner
 * au chatbot un contexte multi-tours. Contrat neutre (types JDK) : l'implémentation (DB, Redis…) reste
 * en {@code internal}. La <b>lecture</b> alimente un {@link ContextSource} « memory » ; l'<b>écriture</b>
 * ({@link #append}) est appelée par l'orchestrateur après chaque échange.
 */
public interface ConversationMemory {

    /**
     * Derniers messages d'une conversation, en ordre <b>chronologique</b> (le plus ancien d'abord).
     *
     * @param conversationId identifiant de conversation
     * @param limit          nombre maximum de messages
     * @return les messages (vide si conversation inconnue)
     */
    List<ConversationMessage> recent(String conversationId, int limit);

    /**
     * Ajoute un message à la conversation.
     *
     * @param conversationId identifiant de conversation
     * @param userId         utilisateur propriétaire (nullable)
     * @param role           {@link ConversationMessage#ROLE_USER} ou {@link ConversationMessage#ROLE_ASSISTANT}
     * @param content        contenu du message
     */
    void append(String conversationId, UUID userId, String role, String content);
}
