package com.onesley.oneclick.core.ai.api;

import java.util.Set;

/**
 * Requête adressée à l'{@link AiOrchestrator} — domaine {@code core/ai}.
 *
 * <p>Porte la question et des préférences d'orchestration optionnelles. Champs à {@code null}/vides =
 * mode <b>AUTO</b> : le {@link RoutingStrategy} décide des sources et de l'usage des outils.
 *
 * @param question       question de l'utilisateur (obligatoire)
 * @param conversationId identifiant de conversation (pour la future mémoire ; nullable)
 * @param sources        sources de contexte imposées par l'appelant (vide = AUTO → décision par la stratégie)
 * @param useTools       forçage du Tool Calling ({@code null} = AUTO)
 */
public record AiQuery(String question, String conversationId, Set<String> sources, Boolean useTools) {

    public AiQuery {
        sources = (sources == null) ? Set.of() : Set.copyOf(sources);
    }

    /** Requête simple en mode AUTO (la stratégie décide de tout). */
    public static AiQuery of(String question) {
        return new AiQuery(question, null, Set.of(), null);
    }
}
