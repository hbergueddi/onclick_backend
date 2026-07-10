package com.onesley.oneclick.core.ai.internal;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration d'infrastructure <b>Groq</b> (chat rapide via connecteur OpenAI-compatible) — module
 * {@code core/ai}. Active uniquement quand {@code app.ai.provider=groq}.
 *
 * <p>Fournit le {@link ChatModel} actif à partir de {@link GroqProperties}. Comme il s'agit du même
 * type {@code ChatModel} LangChain4j que pour Ollama, le service de chat et le Tool Calling sont
 * identiques — seule la config change. Les embeddings restent servis par Ollama (Groq n'en fournit pas).
 *
 * <p><b>Câblage uniquement</b>, aucune logique métier ici.
 */
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "groq")
@Slf4j
class GroqChatConfig {

    @Bean
    ChatModel groqChatModel(GroqProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                "app.ai.provider=groq mais GROQ_API_KEY est absente (app.ai.groq.api-key). "
                    + "Définir la clé dans l'environnement (.env), jamais en dur.");
        }
        log.info("[core/ai] Groq chat model '{}' @ {} (timeout={})",
            props.model(), props.baseUrl(), props.timeout());
        return OpenAiChatModel.builder()
            .baseUrl(props.baseUrl())
            .apiKey(props.apiKey())
            .modelName(props.model())
            .timeout(props.timeout())
            .build();
    }

    /** Nom du modèle de chat actif (Groq) — pour logs/métadonnées côté service. */
    @Bean
    AiChatModelName groqChatModelName(GroqProperties props) {
        return new AiChatModelName(props.model());
    }
}
