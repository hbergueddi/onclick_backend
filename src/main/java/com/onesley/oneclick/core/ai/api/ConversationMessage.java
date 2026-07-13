package com.onesley.oneclick.core.ai.api;

import java.time.Instant;

/**
 * Message d'une conversation chatbot — objet valeur du domaine {@code core/ai}.
 *
 * @param role    émetteur : {@code "user"} ou {@code "assistant"}
 * @param content contenu du message
 * @param at      horodatage
 */
public record ConversationMessage(String role, String content, Instant at) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
}
