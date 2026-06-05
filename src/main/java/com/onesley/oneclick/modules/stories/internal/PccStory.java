package com.onesley.oneclick.modules.stories.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Story tenant (PCC) — contenu éphémère type Instagram (un staff/owner publie, les membres du
 * tenant visionnent). Table {@code pcc_stories} (V72).
 *
 * <p>Le {@code tenantId} est figé à la création (= tenant de l'auteur, résolu via
 * {@code UserDirectoryApi}) : il scope la visibilité (membres/staff du même tenant uniquement) et
 * l'isolation cross-tenant. {@code authorId} = le staff de gestion qui publie.
 *
 * <h3>Workflow (porté par {@code PccStoryService}, PAS par des triggers SQL)</h3>
 * <ul>
 *   <li><b>créée</b> : {@code publishAt <= now} ET ({@code expiresAt} null/futur) → visible des
 *       membres du tenant (carrousel).</li>
 *   <li><b>programmée</b> : {@code publishAt} futur → invisible jusqu'à sa date (le staff la voit).</li>
 *   <li><b>expirée</b> : {@code expiresAt <= now} → sort du feed membre (le staff la voit encore).</li>
 *   <li><b>supprimée</b> : soft-delete ({@code deletedAt}) → invisible de tous.</li>
 * </ul>
 *
 * <p>Le {@code mediaType} est mappé en {@link String} : la colonne est un {@code varchar(16)} avec
 * un CHECK {@code (image|video)} en base (PAS un ENUM Postgres natif — un ENUM natif imposerait un
 * CAST explicite à chaque INSERT/UPDATE Hibernate). Convention alignée sur
 * {@code reservations.status} et {@code tenant_announcements.priority}.
 */
@Entity
@Table(name = "pcc_stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PccStory extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant de la story — scope visibilité + isolation cross-tenant, figé à la création. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Staff/owner auteur de la publication. */
    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    /** URL du média (image/vidéo ; upload côté front via mediaService — hors scope module). Éditable. */
    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    /** {@code image} / {@code video} — CHECK en base (cf. javadoc classe). Éditable. */
    @Column(name = "media_type", nullable = false, length = 16)
    private String mediaType;

    /** Légende courte optionnelle (≤ 280). Éditable. */
    @Column(name = "caption", length = 280)
    private String caption;

    /** Durée d'affichage côté viewer en secondes (1-120, défaut 15). Éditable. */
    @Column(name = "duration_s", nullable = false)
    private int durationS;

    /** Ordre dans le carrousel (asc). Éditable. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Date de publication (programmable). {@code <= now} → visible membre ; futur → programmée. */
    @Column(name = "publish_at", nullable = false)
    private Instant publishAt;

    /** Date d'expiration (sort du feed membre) ou {@code null} (pas d'expiration). Éditable. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Date de soft-delete (invisible de tous) ou {@code null}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Création d'une story. {@code mediaType}/{@code durationS}/{@code sortOrder}/{@code publishAt} fournis. */
    public PccStory(UUID id, UUID tenantId, UUID authorId, String mediaUrl, String mediaType,
                    String caption, int durationS, int sortOrder, Instant publishAt, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.authorId = authorId;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.caption = caption;
        this.durationS = durationS;
        this.sortOrder = sortOrder;
        this.publishAt = publishAt;
        this.expiresAt = expiresAt;
    }

    /**
     * true si la story est <b>vivante et visible des membres</b> à l'instant {@code now} :
     * non supprimée, publiée ({@code publishAt <= now}), non expirée ({@code expiresAt} null ou futur).
     */
    public boolean isVisibleToMembers(Instant now) {
        if (deletedAt != null) return false;
        if (publishAt == null || publishAt.isAfter(now)) return false;
        return expiresAt == null || expiresAt.isAfter(now);
    }

    /**
     * Applique une édition (valeurs déjà résolues/validées par le service). Tous les champs
     * éditables sont remplacés inconditionnellement (sémantique PATCH = état souhaité).
     */
    public void applyEdit(String mediaUrl, String mediaType, String caption, int durationS,
                          int sortOrder, Instant publishAt, Instant expiresAt) {
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.caption = caption;
        this.durationS = durationS;
        this.sortOrder = sortOrder;
        this.publishAt = publishAt;
        this.expiresAt = expiresAt;
    }

    /** Soft-delete (invisible de tous). */
    public void softDelete(Instant when) {
        this.deletedAt = when;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((PccStory) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
