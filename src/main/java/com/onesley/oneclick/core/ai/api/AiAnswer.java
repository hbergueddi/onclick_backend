package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Réponse de l'{@link AiOrchestrator} — domaine {@code core/ai}.
 *
 * <p>Contient la réponse générée et des métadonnées d'orchestration (traçabilité : quelles sources ont
 * contribué, quels outils étaient exposés, combien de fragments de contexte ont été injectés).
 *
 * @param content              réponse textuelle
 * @param contextSourcesUsed   noms des {@link ContextSource} consultées
 * @param toolsAvailable       noms des {@link AiTool} exposés au modèle (exécution câblée ultérieurement)
 * @param contextFragmentCount nombre de fragments de contexte injectés dans le prompt
 */
public record AiAnswer(
    String content,
    List<String> contextSourcesUsed,
    List<String> toolsAvailable,
    int contextFragmentCount
) {
}
