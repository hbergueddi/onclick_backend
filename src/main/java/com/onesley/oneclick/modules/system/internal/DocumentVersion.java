package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.audit.CreatedAuthorEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Révision archivée d'un {@link AppDocument} — historique append-only.
 *
 * <p>Une version est créée par un admin puis immuable : on hérite de
 * {@link CreatedAuthorEntity} ({@code created_at} + {@code created_by}), dont la
 * Javadoc cible explicitement cette table.
 */
@Entity
@Table(name = "document_versions")
@Getter
public class DocumentVersion extends CreatedAuthorEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false, length = 64)
    @Setter private String documentId;

    @Column(name = "version", length = 64)
    @Setter private String version;

    @Column(name = "content", length = 4096)
    @Setter private String content;

    @Column(name = "notes", length = 1024)
    @Setter private String notes;
}
