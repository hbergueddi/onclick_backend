package com.onesley.oneclick.core.ai.api;

import java.util.Map;

/**
 * Port d'un <b>outil métier</b> exposable au LLM (Tool Calling) — domaine {@code core/ai}.
 *
 * <p>Chaque capacité métier (consulter une réservation, chercher un restaurant, lire les points de
 * fidélité…) est un bean implémentant ce port. Le {@link ToolRegistry} les collecte automatiquement :
 * ajouter un outil = ajouter une implémentation, sans toucher à l'orchestrateur.
 *
 * <p><b>Fondation</b> : le contrat est posé, mais aucun outil n'est implémenté et l'exécution effective
 * (binding Tool Calling côté modèle) sera câblée à l'étape « premiers outils métier ».
 */
public interface AiTool {

    /** Nom unique de l'outil (identifiant exposé au modèle). */
    String name();

    /** Description en langage naturel : aide le modèle à décider quand appeler l'outil. */
    String description();

    /**
     * Paramètres attendus : nom → description. Schéma minimal et neutre (un schéma typé plus riche
     * pourra être introduit sans casser les implémentations).
     */
    Map<String, String> parameters();

    /**
     * Exécute l'outil avec les arguments fournis par le modèle.
     *
     * @param arguments arguments nom → valeur
     * @return résultat textuel réinjecté dans la conversation
     */
    String execute(Map<String, Object> arguments);
}
