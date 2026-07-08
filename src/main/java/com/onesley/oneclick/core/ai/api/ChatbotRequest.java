package com.onesley.oneclick.core.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Requête du chatbot — payload de {@code POST /api/ai/chat}.
 *
 * <p>Seule {@code question} est requise. {@code conversationId}, {@code sources} et {@code useTools}
 * sont optionnels : absents, l'orchestration est en mode AUTO (la {@link RoutingStrategy} décide).
 *
 * @param question       question de l'utilisateur (obligatoire)
 * @param conversationId conversation pour la future mémoire (nullable)
 * @param sources        sources de contexte à imposer (ex. {@code ["rag"]}) ; vide/absent = AUTO
 * @param useTools       forçage du Tool Calling ({@code null} = AUTO)
 */
public record ChatbotRequest(
    @NotBlank @Size(min = 1, max = 8000) String question,
    String conversationId,
    Set<String> sources,
    Boolean useTools
) {
    /** Mappe le DTO web vers la requête du domaine. */
    public AiQuery toQuery() {
        return new AiQuery(question, conversationId, sources, useTools);
    }
}
