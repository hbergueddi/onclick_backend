package com.onesley.oneclick.core.ai.api;

/**
 * Port de <b>chat LLM</b> exposé par {@code core/ai} — contrat public, neutre vis-à-vis du provider.
 *
 * <p>Contrat minimal « prompt → réponse ». L'implémentation actuelle est adossée à Ollama
 * (LangChain4j), mais les consommateurs (controllers, services métier) dépendent de cette interface,
 * pas du provider concret : remplacer Ollama par OpenAI / Azure OpenAI / Groq se fait en fournissant
 * une autre implémentation de ce port, <b>sans toucher au code appelant</b>.
 *
 * <p>Point d'extension : de futures surcharges (historique de conversation, options par requête,
 * tool calling) s'ajouteront ici sans casser {@link #chat(String)}.
 */
public interface AiChatApi {

    /**
     * Envoie un prompt utilisateur au modèle et renvoie la réponse générée + ses métadonnées.
     *
     * @param prompt message utilisateur (non nul, non vide)
     * @return le {@link Result} : contenu, modèle ayant répondu, durée d'appel
     */
    Result chat(String prompt);

    /**
     * Variante <b>Tool Calling</b> : expose des {@link AiTool} au modèle, exécute les appels d'outils
     * qu'il demande (boucle interne) et renvoie la réponse finale. Les outils sont des ports du domaine —
     * le mécanisme d'exposition/exécution côté provider reste confiné à l'implémentation.
     *
     * @param prompt message utilisateur
     * @param tools  outils disponibles (liste vide = équivalent à {@link #chat(String)})
     * @return le {@link Result} après résolution des éventuels appels d'outils
     */
    Result chat(String prompt, java.util.List<AiTool> tools);

    /**
     * Résultat neutre d'une génération — indépendant du provider. Chaque implémentation renseigne
     * {@code model} depuis sa propre configuration ; {@code durationMs} mesure l'appel au modèle.
     *
     * @param content    réponse textuelle du modèle
     * @param model      identifiant du modèle ayant répondu (ex. {@code llama3.1})
     * @param durationMs durée de l'appel au modèle, en millisecondes
     */
    record Result(String content, String model, long durationMs) {}
}
