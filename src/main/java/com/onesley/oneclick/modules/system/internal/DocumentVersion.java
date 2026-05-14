package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.audit.CreatedAuthorEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Révision archivée d'un {@link AppDocument} — historique append-only.
 *
 * <p>Une version est créée par un admin puis immuable : on hérite de
 * {@link CreatedAuthorEntity} ({@code created_at} + {@code created_by}), dont la
 * Javadoc cible explicitement cette table.
 */
@Entity
@Table(name = "document_versions")
public class DocumentVersion extends CreatedAuthorEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "version")
    private String version;

    @Column(name = "content")
    private String content;

    @Column(name = "notes")
    private String notes;

    public UUID getId() { return id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
