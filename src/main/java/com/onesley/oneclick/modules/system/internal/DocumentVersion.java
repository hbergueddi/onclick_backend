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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    @Column(name = "document_id", nullable = false)
    @Setter @Size(max = 512) @NotBlank private String documentId;

    @Column(name = "version")
    @Setter @Size(max = 512) private String version;

    @Column(name = "content")
    @Setter @Size(max = 10000) private String content;

    @Column(name = "notes")
    @Setter @Size(max = 2000) private String notes;
}
