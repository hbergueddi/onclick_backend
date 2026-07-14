package com.onesley.oneclick.core.ai.api;

import java.util.Map;

/**
 * Port d'<b>ingestion RAG</b> — domaine {@code core/ai}. Indexe un document texte dans la base de
 * connaissance (chunk → embeddings → stockage vectoriel). L'implémentation ({@code internal}) reste
 * découplée d'Ollama/pgvector.
 */
public interface RagIngestionApi {

    /**
     * Ingère un document texte.
     *
     * @param content  texte du document
     * @param metadata métadonnées de source (propagées aux fragments) — jamais nul
     * @return nombre de fragments indexés
     */
    int ingest(String content, Map<String, Object> metadata);
}
