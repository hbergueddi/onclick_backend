package com.onesley.oneclick.core.ai.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Document du corpus RAG (table {@code rag_documents}) — module {@code core/ai}.
 *
 * <p>Représente une unité de contenu indexée (document ou chunk) et ses métadonnées. La colonne
 * {@code embedding vector(768)} <b>n'est délibérément pas mappée</b> par JPA : le type {@code vector}
 * de pgvector n'est pas géré nativement par Hibernate, et {@code ddl-auto=validate} rejetterait un
 * mapping approximatif. Les écritures/lectures du vecteur passent par du SQL natif dans
 * {@code PgVectorStore} (pattern « read-views/SQL natives » du monolithe). Hibernate ignore les
 * colonnes non mappées, donc la validation de schéma reste verte.
 *
 * <p>Entité en lecture ({@link Immutable}) : l'insertion se fait via {@code PgVectorStore} (SQL natif,
 * pour porter le vecteur en une seule requête) ; cette entité + son repository servent les lectures
 * scalaires du (futur) pipeline RAG.
 */
@Entity
@Table(name = "rag_documents")
@Immutable
@Getter
public class RagDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected RagDocument() {
        // requis par JPA
    }
}
