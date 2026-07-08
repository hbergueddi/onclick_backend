package com.onesley.oneclick.core.ai.api;

import java.util.Set;

/**
 * Décision d'orchestration produite par un {@link RoutingStrategy} — domaine {@code core/ai}.
 *
 * <p>Indique quelles sources de contexte activer et si le Tool Calling est autorisé pour la requête.
 *
 * @param contextSources noms des {@link ContextSource} à interroger (peut être vide)
 * @param useTools       {@code true} si les outils métier doivent être exposés au modèle
 */
public record RoutingDecision(Set<String> contextSources, boolean useTools) {

    public RoutingDecision {
        contextSources = (contextSources == null) ? Set.of() : Set.copyOf(contextSources);
    }

    public static RoutingDecision none() {
        return new RoutingDecision(Set.of(), false);
    }
}
