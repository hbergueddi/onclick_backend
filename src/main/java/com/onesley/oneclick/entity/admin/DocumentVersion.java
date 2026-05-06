package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.document_versions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit partiel created_by inline + created_at sans updated_at, inline.
 */
@Entity
@Table(name = "document_versions")
@EntityListeners(AuditingEntityListener.class)
public class DocumentVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "content", nullable = false)
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "notes")
    private String notes;

    protected DocumentVersion() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getDocumentId() { return documentId; }
    public String getVersion() { return version; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public String getNotes() { return notes; }
}
