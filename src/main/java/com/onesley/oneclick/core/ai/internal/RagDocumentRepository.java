package com.onesley.oneclick.core.ai.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository JPA du corpus RAG — lectures scalaires de {@link RagDocument}.
 *
 * <p>Les opérations vectorielles (insert avec embedding, recherche par similarité) vivent dans
 * {@code PgVectorStore} en SQL natif (le type {@code vector} de pgvector n'étant pas géré par JPA).
 */
interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
}
