package com.onesley.oneclick.core.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête de chat LLM — payload de {@code POST /api/ai/ollama/chat}.
 *
 * <p>Contrat neutre : un simple prompt texte. La taille est bornée pour éviter les payloads abusifs.
 * Des champs optionnels (override de modèle, température, historique) pourront s'ajouter sans casser
 * la compatibilité ascendante.
 *
 * @param prompt message utilisateur (obligatoire, 1..8000 caractères)
 */
public record ChatRequest(
    @NotBlank @Size(min = 1, max = 8000) String prompt
) {
}
