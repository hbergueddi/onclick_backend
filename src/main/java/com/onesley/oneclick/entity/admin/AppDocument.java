package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.app_documents} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "app_documents")
public class AppDocument {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected AppDocument() {
        // JPA
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getVersion() { return version; }
    public UUID getUpdatedBy() { return updatedBy; }
}
