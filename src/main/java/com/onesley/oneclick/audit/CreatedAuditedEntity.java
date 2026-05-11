package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.LastModifiedBy;

import java.util.UUID;

/**
 * Audit avec auteur de création + dernier modificateur — {@code created_at} +
 * {@code created_by} + {@code modified_by} (sans {@code updated_at}).
 *
 * <p>Variante de {@link AuditedEntity} pour les tables qui ont {@code created_by}
 * et {@code modified_by} mais pas {@code updated_at}. Le timestamp de
 * dernière modif n'est pas tracké (probablement pour économie de stockage sur
 * tables à fort volume), mais l'identité du dernier modificateur l'est.
 *
 * <h3>Tables ciblées</h3>
 *
 * <p>Identifiées par audit DB ({@code C-BM}) :
 * <ul>
 *   <li>{@code admin_audit_log} — log centralisé des mutations admin (append-only
 *       en théorie mais peut être édité par superadmin pour corrections).</li>
 *   <li>{@code no_show_disputes} — workflow contestation no-show 48h.</li>
 *   <li>{@code referrals} — programme parrainage (status mutable).</li>
 *   <li>{@code restaurant_staff} — staff resto (statut peut changer).</li>
 *   <li>{@code scanned_tickets} — tickets Snap2Earn (status mutable :
 *       en_attente → valide → rejete).</li>
 * </ul>
 *
 * <h3>ddl-auto=validate</h3>
 *
 * <p>Table SQL doit posséder les 3 colonnes : {@code created_at},
 * {@code created_by uuid}, {@code modified_by uuid}.
 *
 * @see CreatedAuthorEntity
 * @see AuditedEntity
 */
@MappedSuperclass
public abstract class CreatedAuditedEntity extends CreatedAuthorEntity {

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    public UUID getModifiedBy() {
        return modifiedBy;
    }
}
