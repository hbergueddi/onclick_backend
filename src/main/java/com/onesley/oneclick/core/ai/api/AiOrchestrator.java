package com.onesley.oneclick.core.ai.api;

/**
 * <b>Point d'entrée unique du chatbot</b> — orchestre la réponse à une question utilisateur.
 *
 * <p>Pour chaque {@link AiQuery}, l'orchestrateur décide (via un {@link RoutingStrategy}) quelles
 * sources de contexte mobiliser — outils métier ({@link AiTool}), RAG et mémoire (via les
 * {@link ContextSource}) — puis combine le contexte et produit une {@link AiAnswer}. Il peut utiliser
 * une seule source ou plusieurs simultanément.
 *
 * <p><b>Contrat d'architecture</b> : le contrôleur REST ne dépend que de ce port ; l'orchestrateur ne
 * dépend que des ports du domaine ({@link ContextSource}, {@link ToolRegistry}, {@link RoutingStrategy},
 * {@link PromptBuilder}, {@link AiChatApi}) — jamais des implémentations techniques (Ollama, PostgreSQL,
 * LangChain4j).
 */
public interface AiOrchestrator {

    /**
     * Répond à une question en orchestrant les sources de contexte pertinentes.
     *
     * @param query requête utilisateur (question + préférences d'orchestration)
     * @return la réponse générée + métadonnées d'orchestration
     */
    AiAnswer answer(AiQuery query);
}
