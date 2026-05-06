package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.util.UUID;

/**
 * Audit niveau 1 — étend {@link TimestampedEntity} avec l'identité de l'auteur.
 *
 * <p>Une entité qui hérite de {@code AuditedEntity} obtient les 4 colonnes :
 * <ul>
 *   <li>{@code created_at} / {@code updated_at} (héritées de {@link TimestampedEntity})</li>
 *   <li>{@code created_by} — UUID du user authentifié à l'INSERT (immuable)</li>
 *   <li>{@code modified_by} — UUID du user authentifié à chaque UPDATE</li>
 * </ul>
 *
 * <p>Les valeurs {@code *_by} proviennent de {@link SecurityContextAuditorAware}
 * (NULL pour une action système / cron / login flow).
 *
 * <p><b>ddl-auto=validate</b> : la table doit avoir les 4 colonnes — les 2 timestamps
 * + {@code created_by uuid} + {@code modified_by uuid} (nullables tous les deux).
 *
 * <p>Sur le schéma legacy Supabase, seule la table {@code tenants} respecte ce contrat
 * complet aujourd'hui. Les nouvelles tables (Phase 11+) y souscriront. Les autres restent
 * sur {@link TimestampedEntity} ou sans superclass d'audit.
 *
 * @see TimestampedEntity
 */
@MappedSuperclass
public abstract class AuditedEntity extends TimestampedEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getModifiedBy() {
        return modifiedBy;
    }
}
