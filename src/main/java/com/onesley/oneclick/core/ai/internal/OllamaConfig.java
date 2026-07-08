package com.onesley.oneclick.core.ai.internal;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration d'infrastructure Ollama (LangChain4j) — module {@code core/ai}.
 *
 * <p>Rôle strictement limité au <b>câblage</b> : instancie les modèles LangChain4j à partir de
 * {@link OllamaProperties}. <b>Aucune logique métier ici</b> (pas de prompt, pas d'orchestration) —
 * celle-ci vit dans les services ({@code internal}) qui consomment les beans exposés.
 *
 * <h3>Évolutivité</h3>
 * <ul>
 *   <li><b>EmbeddingModel</b> : ajouter un {@code @Bean OllamaEmbeddingModel} basé sur
 *       {@link OllamaProperties#embeddingModel()} (propriété déjà présente).</li>
 *   <li><b>RAG / pgvector</b> : déclarer un {@code EmbeddingStore} (PgVectorEmbeddingStore) +
 *       un {@code ContentRetriever} dans une config dédiée, sans toucher au chat.</li>
 *   <li><b>Tool Calling</b> : le {@link ChatModel} supporte déjà les {@code ChatRequest} avec
 *       {@code ToolSpecification} ; les outils seront enregistrés côté service.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
@Slf4j
class OllamaConfig {

    /**
     * Modèle de chat Ollama, exposé sous l'interface neutre {@link ChatModel} de LangChain4j
     * (découple les consommateurs du provider concret).
     */
    @Bean
    ChatModel ollamaChatModel(OllamaProperties props) {
        log.info("[core/ai] Ollama chat model '{}' @ {} (timeout={})",
            props.chatModel(), props.baseUrl(), props.timeout());
        return OllamaChatModel.builder()
            .baseUrl(props.baseUrl())
            .modelName(props.chatModel())
            .timeout(props.timeout())
            .logRequests(props.logRequests())
            .logResponses(props.logResponses())
            .build();
    }

    /**
     * Modèle d'embeddings Ollama, exposé sous l'interface neutre {@link EmbeddingModel} de
     * LangChain4j. Utilisé par {@code OllamaEmbeddingService} (fondation RAG).
     */
    @Bean
    EmbeddingModel ollamaEmbeddingModel(OllamaProperties props) {
        log.info("[core/ai] Ollama embedding model '{}' @ {} (timeout={})",
            props.embeddingModel(), props.baseUrl(), props.timeout());
        return OllamaEmbeddingModel.builder()
            .baseUrl(props.baseUrl())
            .modelName(props.embeddingModel())
            .timeout(props.timeout())
            .logRequests(props.logRequests())
            .logResponses(props.logResponses())
            .build();
    }
}
