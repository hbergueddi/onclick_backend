package com.onesley.oneclick.core.ai.api;

/**
 * Réponse de chat LLM — corps de {@code POST /api/ai/ollama/chat}.
 *
 * @param content    réponse générée par le modèle
 * @param model      identifiant du modèle ayant répondu (ex. {@code llama3.1})
 * @param durationMs durée de l'appel au modèle, en millisecondes (diagnostic)
 */
public record ChatResponse(
    String content,
    String model,
    long durationMs
) {
    /** Mappe le résultat neutre du port vers le DTO de réponse HTTP. */
    public static ChatResponse from(AiChatApi.Result result) {
        return new ChatResponse(result.content(), result.model(), result.durationMs());
    }
}
