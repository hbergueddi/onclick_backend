package com.onesley.oneclick.core.ai.api;

/**
 * Résultat d'une recherche par similarité dans le {@link VectorStore} — domaine {@code core/ai}.
 *
 * @param chunk le fragment retrouvé (avec son identifiant et ses métadonnées)
 * @param score similarité cosinus dans {@code [0..1]} (1 = identique), triée décroissante par le store
 */
public record SearchResult(DocumentChunk chunk, double score) {
}
