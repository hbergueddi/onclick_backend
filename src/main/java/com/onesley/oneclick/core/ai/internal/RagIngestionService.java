package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.Chunker;
import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.EmbeddingService;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.core.ai.api.RagIngestionApi;
import com.onesley.oneclick.core.ai.api.RawDocument;
import com.onesley.oneclick.core.ai.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Pipeline d'<b>ingestion RAG</b> — module {@code core/ai}.
 *
 * <p>Découpe un document ({@link Chunker}), calcule les embeddings ({@link EmbeddingService}) et
 * persiste chaque fragment dans le {@link VectorStore}. Ne dépend que des ports du domaine — aucun
 * couplage à Ollama/pgvector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class RagIngestionService implements RagIngestionApi {

    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    /**
     * Ingère un document texte : chunk → embed → store.
     *
     * @param content  texte du document
     * @param metadata métadonnées de source (source, titre…) — propagées à chaque fragment
     * @return nombre de fragments stockés
     */
    @Override
    @Transactional
    public int ingest(String content, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = chunker.chunk(new RawDocument(content, metadata));
        if (chunks.isEmpty()) return 0;

        List<EmbeddingVector> vectors = embeddingService.embedAll(
            chunks.stream().map(DocumentChunk::content).toList());

        int n = Math.min(chunks.size(), vectors.size());
        for (int i = 0; i < n; i++) {
            vectorStore.add(chunks.get(i), vectors.get(i));
        }
        log.info("[core/ai] RAG ingest — {} fragment(s) stockés", n);
        return n;
    }
}
