package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Port d'une <b>source de contexte</b> consultable par l'{@link AiOrchestrator} — domaine {@code core/ai}.
 *
 * <p>Abstraction extensible : chaque manière d'enrichir une question est une {@code ContextSource}
 * distincte (RAG adossé au {@link VectorStore}, mémoire de conversation, etc.). Ajouter une source
 * revient à fournir un nouveau bean implémentant ce port — l'orchestrateur les découvre et les combine
 * sans modification. Aucune source ne dépend d'une autre.
 *
 * <p><b>Fondation</b> : aucune implémentation à ce stade (le RAG et la mémoire viendront). En l'absence
 * d'implémentation, l'orchestrateur fonctionne en direct (LLM sans contexte).
 */
public interface ContextSource {

    /**
     * Identifiant logique et stable de la source (ex. {@code "rag"}, {@code "memory"}). Sert au
     * {@link RoutingStrategy} pour sélectionner les sources à activer.
     */
    String name();

    /**
     * Récupère les fragments de contexte pertinents pour la requête.
     *
     * @param query requête utilisateur
     * @return fragments (peut être vide, jamais nul)
     */
    List<ContextFragment> retrieve(AiQuery query);
}
