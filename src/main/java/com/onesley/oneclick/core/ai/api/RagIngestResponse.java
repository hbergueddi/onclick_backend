package com.onesley.oneclick.core.ai.api;

/**
 * Réponse d'ingestion RAG — corps de {@code POST /api/ai/rag/documents}.
 *
 * @param chunksIngested nombre de fragments indexés (chunk + embedding + stockage vectoriel)
 */
public record RagIngestResponse(int chunksIngested) {
}
