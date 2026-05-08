package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.shared.AnnouncementPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.tenant_announcements} — annonces par tenant
 * (mécanique : publication différée + Realtime + cron + cascade Apple Wallet).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes : created_at + updated_at + created_by + modified_by).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id NOT NULL} → {@link Tenant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code author_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code target_restaurant_ids uuid[]} → array natif PostgreSQL,
 *       <b>pas de jointure JPA possible</b> (Hibernate ne mappe pas les arrays
 *       d'UUID comme {@code @ManyToMany}). Reste {@code List<UUID>}.</li>
 * </ul>
 */
@Entity
@Table(name = "tenant_announcements")
public class TenantAnnouncement extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure tenant_id ─────────────────────────────────────────────────
    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ─── Jointure author_id ─────────────────────────────────────────────────
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false)
    private UUID authorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Profile author;

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "priority", nullable = false, columnDefinition = "announcement_priority")
    private AnnouncementPriority priority;

    @NotNull
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Column(name = "publish_at", nullable = false)
    private Instant publishAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @NotNull
    @Column(name = "body_version", nullable = false)
    private Integer bodyVersion;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_restaurant_ids", columnDefinition = "uuid[]")
    private List<UUID> targetRestaurantIds = new ArrayList<>();

    protected TenantAnnouncement() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getTenantId() { return tenantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getAuthorId() { return authorId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getAuthor() { return author; }
    public void setAuthor(Profile author) { this.author = author; }

    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getImageUrl() { return imageUrl; }
    public AnnouncementPriority getPriority() { return priority; }
    public Boolean getIsPinned() { return isPinned; }
    public Instant getPublishAt() { return publishAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Integer getBodyVersion() { return bodyVersion; }
    public Instant getPushSentAt() { return pushSentAt; }
    public List<UUID> getTargetRestaurantIds() { return targetRestaurantIds; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

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
        TenantAnnouncement that = (TenantAnnouncement) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
