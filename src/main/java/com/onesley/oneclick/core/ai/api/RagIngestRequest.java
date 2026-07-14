package com.onesley.oneclick.core.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête d'ingestion RAG — payload de {@code POST /api/ai/rag/documents}.
 *
 * @param content texte du document à indexer (obligatoire)
 * @param source  libellé de source optionnel (nom de fichier, URL, rubrique…) porté en métadonnée
 */
public record RagIngestRequest(
    @NotBlank @Size(min = 1, max = 100_000) String content,
    @Size(max = 256) String source
) {
}
