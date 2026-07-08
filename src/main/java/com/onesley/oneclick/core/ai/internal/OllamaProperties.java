package com.onesley.oneclick.core.ai.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration externalisée du serveur Ollama (LangChain4j) — préfixe {@code app.ai.ollama}.
 *
 * <p>Record immuable liée par constructeur ({@code @ConfigurationProperties} sur record). Toute la
 * configuration vit dans {@code application.yml} (aucune valeur en dur, aucun {@code @Value} dispersé) :
 * <pre>
 * app:
 *   ai:
 *     ollama:
 *       base-url: http://localhost:11434
 *       chat-model: llama3.1
 *       embedding-model: nomic-embed-text
 *       timeout: PT60S
 *       log-requests: false
 *       log-responses: false
 * </pre>
 *
 * <p>{@code embeddingModel} n'est pas encore consommé : il est déclaré dès maintenant pour préparer
 * l'ajout d'un {@code EmbeddingModel} (RAG / pgvector) sans refonte de la configuration.
 *
 * @param baseUrl        URL du serveur Ollama (ex. {@code http://localhost:11434})
 * @param chatModel      nom du modèle de génération (ex. {@code llama3.1})
 * @param embeddingModel nom du modèle d'embeddings (ex. {@code nomic-embed-text}) — usage futur (RAG)
 * @param timeout        timeout des appels HTTP (format ISO-8601, ex. {@code PT60S})
 * @param logRequests    trace les requêtes sortantes (debug — jamais {@code true} en prod)
 * @param logResponses   trace les réponses (debug — jamais {@code true} en prod)
 */
@ConfigurationProperties(prefix = "app.ai.ollama")
public record OllamaProperties(
    @DefaultValue("http://localhost:11434") String baseUrl,
    @DefaultValue("llama3.1") String chatModel,
    @DefaultValue("nomic-embed-text") String embeddingModel,
    @DefaultValue("PT60S") Duration timeout,
    @DefaultValue("false") boolean logRequests,
    @DefaultValue("false") boolean logResponses
) {
}
