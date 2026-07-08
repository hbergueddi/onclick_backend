package com.onesley.oneclick.core.ai.api;

import java.util.List;
import java.util.Optional;

/**
 * Port de <b>catalogue d'outils métier</b> — domaine {@code core/ai}.
 *
 * <p>Expose l'ensemble des {@link AiTool} disponibles à l'{@link AiOrchestrator}, sans que celui-ci
 * connaisse comment ils sont découverts. L'implémentation agrège les outils enregistrés (beans Spring).
 */
public interface ToolRegistry {

    /** Tous les outils disponibles (peut être vide). */
    List<AiTool> all();

    /** Recherche un outil par son nom. */
    Optional<AiTool> byName(String name);
}
