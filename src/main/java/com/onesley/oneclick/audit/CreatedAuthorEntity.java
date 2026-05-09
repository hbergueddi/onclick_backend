package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;

import java.util.UUID;

/**
 * Audit avec auteur de création seul — {@code created_at} + {@code created_by}
 * (sans {@code updated_at} ni {@code modified_by}).
 *
 * <p>Couvre les tables de versioning append-only : une row est créée par un
 * auteur identifié, puis immuable. Pas de notion d'update donc pas de
 * {@code modified_by}.
 *
 * <h3>Tables ciblées</h3>
 *
 * <p>Identifiées par audit DB ({@code C-B-}) :
 * <ul>
 *   <li>{@code document_versions} — historique des versions d'un AppDocument
 *       (CGU, confidentialité…). Chaque version est créée par un admin, jamais
 *       modifiée ; une nouvelle version = nouvelle row.</li>
 * </ul>
 *
 * <h3>ddl-auto=validate</h3>
 *
 * <p>Table SQL doit posséder {@code created_at} + {@code created_by uuid}.
 *
 * @see CreatedAtEntity
 * @see CreatedAuditedEntity
 */
@MappedSuperclass
public abstract class CreatedAuthorEntity extends CreatedAtEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public UUID getCreatedBy() {
        return createdBy;
    }
}
