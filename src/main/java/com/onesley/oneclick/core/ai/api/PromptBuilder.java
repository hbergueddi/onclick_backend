package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Port de <b>construction de prompt</b> — domaine {@code core/ai}.
 *
 * <p>Assemble le prompt final envoyé au LLM à partir d'une question et du contexte agrégé par
 * l'{@link AiOrchestrator}. Consomme des {@link ContextFragment} neutres (issus de n'importe quelle
 * {@link ContextSource} : RAG, mémoire…), donc indépendant de l'origine du contexte. Isole la stratégie
 * de prompting (format, consignes, citation des sources, garde-fous anti-hallucination).
 */
public interface PromptBuilder {

    /**
     * Construit le prompt à soumettre au modèle.
     *
     * @param question question de l'utilisateur
     * @param context  fragments de contexte agrégés (peut être vide)
     * @return le prompt complet prêt pour {@link AiChatApi}
     */
    String build(String question, List<ContextFragment> context);
}
