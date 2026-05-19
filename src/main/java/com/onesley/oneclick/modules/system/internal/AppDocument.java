package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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
@Getter
public class AppDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Setter private String id;

    @Column(name = "content")
    @Setter private String content;

    @Column(name = "version")
    @Setter private String version;

    @Column(name = "updated_at", nullable = false)
    @Setter private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    @Setter private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter private Instant createdAt = Instant.now();
}
