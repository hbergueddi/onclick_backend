package com.onesley.oneclick.modules.announcement.internal;

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
 * Annonce tenant (Lot 8) — comm B2B staff descendante (un tenant-admin publie, les owners/staff
 * du tenant lisent et acquittent). Table {@code tenant_announcements} (V70).
 *
 * <p>Le {@code tenantId} est figé à la création (= tenant de l'auteur, résolu via
 * {@code UserDirectoryApi}) : il scope la visibilité (staff/admins du même tenant uniquement) et
 * l'isolation cross-tenant. {@code authorId} = le tenant-admin qui publie.
 *
 * <h3>Workflow (porté par {@code AnnouncementService}, PAS par des triggers SQL)</h3>
 * <ul>
 *   <li><b>scheduled</b> : {@code publishAt} futur → visible des seuls tenant-admins.</li>
 *   <li><b>published</b> : {@code publishAt <= now} → visible du staff actif du tenant.</li>
 *   <li><b>archived</b> : {@code archivedAt} posé → sort de la bannière (reste visible admin).</li>
 *   <li><b>deleted</b> : soft-delete ({@code deletedAt}) → invisible de tous (même admin).</li>
 * </ul>
 *
 * <h3>Règles métier (service)</h3>
 * <ul>
 *   <li><b>Épinglage</b> : au plus 1 annonce épinglée PAR priorité PAR tenant. Épingler une
 *       annonce désépingle les autres de la même priorité/tenant ({@link #pin()} +
 *       {@code AnnouncementRepository.unpinOthers}).</li>
 *   <li><b>body_version</b> : éditer le {@code body} incrémente {@link #bodyVersion} et purge les
 *       acquittements (ré-acquittement requis). Géré dans {@code AnnouncementService.update}.</li>
 * </ul>
 *
 * <p>Le {@code priority} est mappé en {@link String} : la colonne est un {@code varchar(16)} avec
 * un CHECK {@code (urgent|permanent)} en base (PAS un ENUM Postgres natif — un ENUM natif imposerait
 * un CAST explicite à chaque INSERT/UPDATE Hibernate). Convention alignée sur
 * {@code reservations.status}. Valeurs {@code urgent}/{@code permanent}.
 */
@Entity
@Table(name = "tenant_announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant de l'annonce — scope visibilité + isolation cross-tenant, figé à la création. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Tenant-admin auteur de la publication. */
    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    /** Titre (1-100). Éditable. */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** Corps (1-1000). Éditable — toute modification bump {@link #bodyVersion} (service). */
    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    /** Illustration optionnelle (URL média ; upload côté front — V1 hors scope). */
    @Column(name = "image_url")
    private String imageUrl;

    /** {@code urgent} / {@code permanent} — ENUM pg mappé String (cf. javadoc classe). */
    @Column(name = "priority", nullable = false, length = 16)
    private String priority;

    /** true = épinglée (en tête). Max 1 par priorité/tenant (invariant service). */
    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    /** Date de publication (programmable). {@code <= now} → visible staff ; futur → admin seul. */
    @Column(name = "publish_at", nullable = false)
    private Instant publishAt;

    /** Date d'archivage (sort de la bannière) ou {@code null}. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Date de soft-delete (invisible de tous) ou {@code null}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Version du corps — bump à chaque édition du {@code body} → purge des acquittements. */
    @Column(name = "body_version", nullable = false)
    private int bodyVersion;

    /** Horodatage du dernier push envoyé (anti double-push) — V1 hors scope (cron). */
    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    /** Création d'une annonce. {@code bodyVersion} démarre à 1 ; pinned/publishAt fournis. */
    public Announcement(UUID id, UUID tenantId, UUID authorId, String title, String body,
                        String imageUrl, String priority, boolean pinned, Instant publishAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.authorId = authorId;
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.priority = priority;
        this.pinned = pinned;
        this.publishAt = publishAt;
        this.bodyVersion = 1;
    }

    /** true si l'annonce est publiée (publishAt atteint) à l'instant {@code now}. */
    public boolean isPublished(Instant now) {
        return publishAt != null && !publishAt.isAfter(now);
    }

    /**
     * B13 — marque l'annonce comme « notifiée » : pose {@link #pushSentAt}. Sert d'idempotence au
     * cron de publication différée (le partial index {@code WHERE push_sent_at IS NULL} ne capte plus
     * cette ligne) ET au chemin de publication immédiate de {@code AnnouncementService} (qui stampe
     * aussi, pour que le cron ne re-notifie jamais une annonce déjà notifiée à la création).
     * Idempotent (re-stamp inoffensif si déjà posé).
     */
    public void markNotified(Instant when) {
        this.pushSentAt = when;
    }

    /** true si l'annonce est vivante (ni archivée ni soft-deletée). */
    public boolean isLive() {
        return archivedAt == null && deletedAt == null;
    }

    /**
     * Applique une édition. Le titre/imageUrl/priority/pinned/publishAt sont remplacés
     * inconditionnellement (valeurs déjà résolues par le service) ; le {@code body} n'est
     * remplacé QUE s'il change, auquel cas {@link #bodyVersion} est incrémenté (le service purge
     * alors les acquittements). Retourne true si le body a changé (→ reset reads requis).
     */
    public boolean applyEdit(String title, String body, String imageUrl, String priority,
                             boolean pinned, Instant publishAt) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.priority = priority;
        this.pinned = pinned;
        this.publishAt = publishAt;
        boolean bodyChanged = !Objects.equals(this.body, body);
        if (bodyChanged) {
            this.body = body;
            this.bodyVersion = this.bodyVersion + 1;
        }
        return bodyChanged;
    }

    /** Épingle l'annonce (le service désépingle ensuite les autres de la même priorité/tenant). */
    public void pin() {
        this.pinned = true;
    }

    /** Désépingle l'annonce. */
    public void unpin() {
        this.pinned = false;
    }

    /** Archive l'annonce (sort de la bannière, reste visible admin). */
    public void archive(Instant when) {
        this.archivedAt = when;
        this.pinned = false; // une annonce archivée n'est plus épinglée
    }

    /** Soft-delete (invisible de tous, même admin). */
    public void softDelete(Instant when) {
        this.deletedAt = when;
        this.pinned = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((Announcement) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
