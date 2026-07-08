package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Port de <b>routage</b> — décide, pour une requête, quelles sources de contexte et quels mécanismes
 * activer. Domaine {@code core/ai}.
 *
 * <p>Isole la logique de décision de l'orchestrateur : une stratégie par règles aujourd'hui, une
 * stratégie pilotée par le LLM demain, sans changer l'orchestrateur ni le contrôleur.
 */
public interface RoutingStrategy {

    /**
     * Décide de l'orchestration pour une requête.
     *
     * @param query            requête (peut imposer des sources / le Tool Calling, sinon AUTO)
     * @param availableSources noms des {@link ContextSource} réellement disponibles dans le contexte
     * @return la décision (sources à interroger + activation des outils)
     */
    RoutingDecision decide(AiQuery query, List<String> availableSources);
}
