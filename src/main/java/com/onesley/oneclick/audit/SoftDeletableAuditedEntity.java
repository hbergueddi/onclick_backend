package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Audit niveau 2 — la base de toutes les entités métier enterprise.
 *
 * <p>Couvre les 5 colonnes standard de l'architecture enterprise (§16 du document
 * {@code oneclick_architecture_enterprise_optimized.md}) :
 * <ul>
 *   <li>{@code created_at} (hérité de {@link TimestampedEntity})</li>
 *   <li>{@code updated_at} (hérité)</li>
 *   <li>{@code deleted_at} — soft delete, {@code null} = ligne active</li>
 *   <li>{@code created_by} — UUID du user à l'INSERT</li>
 *   <li>{@code updated_by} — UUID du user à chaque UPDATE</li>
 * </ul>
 *
 * <p><b>Soft delete obligatoire</b> : toutes les requêtes lecture doivent filtrer
 * {@code WHERE deleted_at IS NULL}. Pattern via {@code @SQLRestriction} ou
 * Specification dédiée — convention à appliquer dans les repositories.
 *
 * <p>Les valeurs {@code *_by} proviennent de {@link SecurityContextAuditorAware}
 * (NULL pour une action système / cron / signup public).
 *
 * <p><b>ddl-auto=validate</b> : table doit posséder
 * {@code created_at + updated_at + deleted_at + created_by + updated_by}.
 *
 * @see TimestampedEntity
 * @see AuditedEntity
 */
@MappedSuperclass
@Getter
public abstract class SoftDeletableAuditedEntity extends TimestampedEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Soft delete — à appeler dans le service. */
    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
