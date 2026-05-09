package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.tenant_admins} — junction table user ↔ tenant avec rôle.
 *
 * <p>Pattern : <i>PK composite (tenant_id, user_id) via {@code @IdClass} +
 * {@code @MapsId} sur les jointures</i>.
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
 * <h3>Jointures JPA (passe 3) — pattern critique {@code @MapsId}</h3>
 * <p>Quand une FK fait partie de la PK composite, Hibernate aurait deux mappings
 * concurrents pour la même colonne (un comme {@code @Id UUID}, un comme
 * {@code @JoinColumn}) et planterait au démarrage. {@code @MapsId("tenantId")}
 * indique que le {@code @Id tenantId} est dérivé de la FK Tenant.
 *
 * <ul>
 *   <li>{@code @MapsId("tenantId")} → le @Id tenantId est dérivé de tenant.id</li>
 *   <li>{@code @MapsId("userId")} → le @Id userId est dérivé de user.id</li>
 *   <li>{@code invited_by} → reste UUID brut (audit field, convention)</li>
 * </ul>
 *
 * <p>Particularités du schéma :
 * <ul>
 *   <li>Pas de {@code updated_at} → on déclare juste {@code created_at} à la main
 *       (pas d'héritage {@link com.onesley.oneclick.audit.TimestampedEntity}) avec
 *       un AuditingEntityListener au niveau entité</li>
 *   <li>{@code role text} avec CHECK (owner/admin/viewer) → mapping String, validation
 *       déléguée au service métier</li>
 * </ul>
 */
@Entity
@Table(name = "tenant_admins")
@IdClass(TenantAdminId.class)
public class TenantAdmin extends CreatedAtEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // ─── Jointure tenant (PK partielle via @MapsId) ─────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private Tenant tenant;

    // ─── Jointure user (PK partielle via @MapsId) ───────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Profile user;

    @Column(name = "role", nullable = false)
    private String role;

    /** Audit field : UUID brut (convention senior, pas de jointure auto). */
    @Column(name = "invited_by")
    private UUID invitedBy;

    protected TenantAdmin() {
        // JPA
    }

    public TenantAdmin(Tenant tenant, Profile user, String role, UUID invitedBy) {
        this.tenant = tenant;
        this.user = user;
        // tenantId/userId sont dérivés via @MapsId au flush
        this.role = role;
        this.invitedBy = invitedBy;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public UUID getInvitedBy() { return invitedBy; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────
    // PK composite — utilise (tenantId, userId).

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        TenantAdmin that = (TenantAdmin) o;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
