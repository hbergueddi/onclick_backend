package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit niveau 1 — colonnes communes à toute entité métier.
 *
 * <p>Une entité qui hérite de {@code BaseEntity} obtient automatiquement :
 * <ul>
 *   <li>{@code created_by} / {@code created_at} — renseignés à l'INSERT (immutables)</li>
 *   <li>{@code modified_by} / {@code modified_at} — renseignés à l'INSERT et à chaque UPDATE</li>
 * </ul>
 *
 * <p>Les valeurs {@code *_by} sont alimentées via {@link SecurityContextAuditorAware}
 * (UUID du user authentifié, ou {@code NULL} pour une action système / anonyme).
 *
 * <p>Les valeurs {@code *_at} sont stockées en UTC ({@link Instant}). Les conventions
 * d'application Spring : {@code spring.jpa.properties.hibernate.jdbc.time_zone=UTC}.
 *
 * <p><b>ddl-auto=validate</b> : pour qu'une entité étende {@code BaseEntity}, sa table SQL
 * <i>doit</i> posséder les 4 colonnes ci-dessous (types {@code uuid} et {@code timestamptz}).
 * Si la table actuelle utilise {@code updated_at} au lieu de {@code modified_at}, soit on
 * ajoute la colonne via migration Flyway, soit on n'étend pas {@code BaseEntity}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getModifiedBy() {
        return modifiedBy;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
