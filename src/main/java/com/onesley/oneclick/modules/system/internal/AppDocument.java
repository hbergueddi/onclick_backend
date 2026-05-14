package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Document interne versionné — page admin {@code DocumentExport}.
 *
 * <p>La clé primaire est un identifiant logique stable ({@code "plan"} pour la
 * documentation d'architecture). Le contenu courant est upserté ; chaque
 * révision est archivée séparément dans {@link DocumentVersion}.
 *
 * <p>Entité « plate » volontairement : l'{@code id} est un {@code String}
 * fourni par l'appelant (pas de {@code @GeneratedValue}), et {@code updated_at}
 * est positionné manuellement à chaque upsert — d'où l'absence d'héritage des
 * superclasses d'audit (incompatibles avec une PK textuelle).
 */
@Entity
@Table(name = "app_documents")
public class AppDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "content")
    private String content;

    @Column(name = "version")
    private String version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
