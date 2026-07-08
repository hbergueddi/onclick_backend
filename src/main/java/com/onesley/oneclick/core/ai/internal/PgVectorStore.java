package com.onesley.oneclick.core.ai.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.core.ai.api.SearchResult;
import com.onesley.oneclick.core.ai.api.VectorStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation PostgreSQL/pgvector du port {@link VectorStore} (module {@code core/ai}).
 *
 * <p>Le type {@code vector} de pgvector n'étant pas géré par JPA/Hibernate, les opérations sur la
 * colonne {@code embedding} utilisent du <b>SQL natif</b> :
 * <ul>
 *   <li>insertion : {@code CAST(:embedding AS vector)} + {@code CAST(:metadata AS jsonb)} ;</li>
 *   <li>recherche : opérateur de distance cosinus {@code <=>} (aligné sur l'index HNSW
 *       {@code vector_cosine_ops}), score de similarité = {@code 1 - distance}.</li>
 * </ul>
 *
 * <p>Aucun type pgvector/Hibernate ne fuit hors de cette classe : le port ne manipule que des objets
 * métier ({@link DocumentChunk}, {@link EmbeddingVector}, {@link SearchResult}). Remplacer Postgres par
 * une autre base vectorielle = fournir une autre implémentation de {@link VectorStore}.
 *
 * <p>Observabilité : insertions et recherches sont chronométrées via {@link AiMetrics}.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
class PgVectorStore implements VectorStore {

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;
    private final AiMetrics metrics;

    @Override
    @Transactional
    public UUID add(DocumentChunk chunk, EmbeddingVector embedding) {
        return metrics.recordVectorAdd(() -> {
            UUID id = UUID.randomUUID();
            em.createNativeQuery("""
                    INSERT INTO rag_documents (id, content, metadata, embedding)
                    VALUES (:id, :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector))
                    """)
                .setParameter("id", id)
                .setParameter("content", chunk.content())
                .setParameter("metadata", toJson(chunk.metadata()))
                .setParameter("embedding", toVectorLiteral(embedding))
                .executeUpdate();
            log.debug("[core/ai] vector stored id={} dims={}", id, embedding.dimension());
            return id;
        });
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<SearchResult> search(EmbeddingVector queryEmbedding, int topK) {
        return metrics.recordVectorSearch(() -> {
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT id, content, metadata::text AS metadata,
                           1 - (embedding <=> CAST(:q AS vector)) AS score
                      FROM rag_documents
                     WHERE embedding IS NOT NULL
                     ORDER BY embedding <=> CAST(:q AS vector)
                    """)
                .setParameter("q", toVectorLiteral(queryEmbedding))
                .setMaxResults(Math.max(1, topK))
                .getResultList();

            return rows.stream()
                .map(r -> new SearchResult(
                    new DocumentChunk(toUuid(r[0]), (String) r[1], fromJson((String) r[2])),
                    ((Number) r[3]).doubleValue()))
                .toList();
        });
    }

    // ─── Helpers (mapping objets métier ↔ formats Postgres) ───────────────────────

    /** Formate un {@link EmbeddingVector} au littéral pgvector {@code [f1,f2,...]} (décimales '.'). */
    private static String toVectorLiteral(EmbeddingVector vector) {
        float[] v = vector.toArray();
        StringBuilder sb = new StringBuilder(v.length * 8 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]); // String.valueOf(float) → indépendant de la locale
        }
        return sb.append(']').toString();
    }

    private static UUID toUuid(Object raw) {
        return (raw instanceof UUID u) ? u : UUID.fromString(String.valueOf(raw));
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalArgumentException("Métadonnées non sérialisables en JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Métadonnées JSON illisibles", e);
        }
    }
}
