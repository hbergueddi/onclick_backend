package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.admin_audit_log} — audit centralisé des mutations admin
 * (instrumenté via helper {@code action-log-types.ts} sur 4 EFs).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code actor_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (peut être un job system sans actor humain).</li>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code entity_id} : <b>polymorphique</b> (peut pointer vers reservations,
 *       restaurants, profiles, contracts…) → reste UUID brut + {@code entity_type}
 *       discriminator. Pattern futur {@code @Any} Hibernate si besoin réel.</li>
 *   <li>{@code created_by} / {@code modified_by} : audits, restent UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "admin_audit_log")
@EntityListeners(AuditingEntityListener.class)
public class AdminAuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", insertable = false, updatable = false)
    private UUID actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Profile actor;

    @Email
    @NotBlank
    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @NotBlank
    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    /** UUID polymorphique : pas de jointure (cible varie selon entity_type). */
    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_label")
    private String entityLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff", columnDefinition = "jsonb")
    private Map<String, Object> diff = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** Audit field : UUID brut. */
    @Column(name = "created_by")
    private UUID createdBy;

    /** Audit field : UUID brut. */
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected AdminAuditLog() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public Profile getActor() { return actor; }
    public void setActor(Profile actor) { this.actor = actor; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getEntityLabel() { return entityLabel; }
    public Map<String, Object> getDiff() { return diff; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }

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
        AdminAuditLog that = (AdminAuditLog) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
