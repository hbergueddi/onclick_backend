package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.tenant_admins} — junction table user ↔ tenant avec rôle.
 *
 * <p>Pattern pilote : <i>PK composite (tenant_id, user_id) via {@code @IdClass}</i>.
 *
 * <p>Pourquoi {@code @IdClass} plutôt que {@code @EmbeddedId} :
 * <ul>
 *   <li>Les champs sont accessibles directement (ex: {@code tenantAdmin.getTenantId()})
 *       sans passer par un wrapper {@code id}</li>
 *   <li>Les queries Spring Data dérivées peuvent référencer les champs par leur
 *       nom natif (ex: {@code findByTenantIdAndUserId})</li>
 *   <li>Dans le code métier, l'identifiant composite n'est manipulé qu'au niveau
 *       du repo (pour les {@code findById}), pas dans la business logic</li>
 * </ul>
 *
 * <p>Particularités du schéma :
 * <ul>
 *   <li>Pas de {@code updated_at} → on déclare juste {@code created_at} à la main
 *       (pas d'héritage {@link com.onesley.oneclick.audit.TimestampedEntity}) avec
 *       un AuditingEntityListener au niveau entité</li>
 *   <li>{@code role text} avec CHECK (owner/admin/viewer) → mapping String, validation
 *       déléguée au service métier</li>
 *   <li>{@code invited_by uuid} → FK auth.users, mappée en UUID brut</li>
 * </ul>
 */
@Entity
@Table(name = "tenant_admins")
@IdClass(TenantAdminId.class)
@EntityListeners(AuditingEntityListener.class)
public class TenantAdmin {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected TenantAdmin() {
        // JPA
    }

    public TenantAdmin(UUID tenantId, UUID userId, String role, UUID invitedBy) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
        this.invitedBy = invitedBy;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
    public UUID getInvitedBy() { return invitedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRole(String role) {
        this.role = role;
    }
}
