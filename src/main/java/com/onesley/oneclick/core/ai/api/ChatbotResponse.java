package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Réponse du chatbot — corps de {@code POST /api/ai/chat}.
 *
 * @param answer               réponse générée
 * @param contextSourcesUsed   sources de contexte ayant contribué
 * @param toolsAvailable       outils métier exposés au modèle
 * @param contextFragmentCount nombre de fragments de contexte injectés
 */
public record ChatbotResponse(
    String answer,
    List<String> contextSourcesUsed,
    List<String> toolsAvailable,
    int contextFragmentCount
) {
    public static ChatbotResponse from(AiAnswer a) {
        return new ChatbotResponse(a.content(), a.contextSourcesUsed(), a.toolsAvailable(), a.contextFragmentCount());
    }
}
