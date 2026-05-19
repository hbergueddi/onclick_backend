package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import lombok.Getter;

/**
 * Audit niveau 0 — minimal commun aux ~70 % des tables Supabase legacy.
 *
 * <p>Couvre uniquement les 2 colonnes timestamps : {@code created_at}
 * et {@code updated_at}. C'est la convention historique Supabase, encore
 * en place dans la majorité du schéma OneClick.
 *
 * <p>Une entité qui hérite de {@code TimestampedEntity} obtient :
 * <ul>
 *   <li>{@code created_at} — renseigné à l'INSERT, immuable, UTC ({@link Instant})</li>
 *   <li>{@code updated_at} — renseigné à l'INSERT et à chaque UPDATE, UTC</li>
 * </ul>
 *
 * <p><b>ddl-auto=validate</b> : pour qu'une entité étende cette classe, sa table SQL
 * <i>doit</i> posséder les 2 colonnes (types {@code timestamptz NOT NULL}).
 *
 * <p>Pour ajouter en plus l'audit de l'identité (createdBy/modifiedBy), étendre
 * {@link AuditedEntity} à la place.
 *
 * @see AuditedEntity
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class TimestampedEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
