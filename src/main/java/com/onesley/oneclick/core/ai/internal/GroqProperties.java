package com.onesley.oneclick.core.ai.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration du provider de chat <b>Groq</b> (API OpenAI-compatible) — préfixe {@code app.ai.groq}.
 *
 * <p>Réutilise les clés existantes {@code api-key} et {@code model} (déjà présentes pour le client Groq
 * legacy) et ajoute {@code base-url}/{@code timeout} pour le {@code ChatModel} LangChain4j. La clé API
 * provient de l'environnement ({@code GROQ_API_KEY}) — jamais codée en dur.
 *
 * @param baseUrl endpoint OpenAI-compatible de Groq
 * @param apiKey  clé API Groq (externalisée : {@code GROQ_API_KEY})
 * @param model   modèle de chat (ex. {@code llama-3.3-70b-versatile})
 * @param timeout timeout des appels (ISO-8601)
 */
@ConfigurationProperties(prefix = "app.ai.groq")
public record GroqProperties(
    @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
    @DefaultValue("") String apiKey,
    @DefaultValue("llama-3.3-70b-versatile") String model,
    @DefaultValue("PT60S") Duration timeout
) {
}
