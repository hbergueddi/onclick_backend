package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.announcement_reads} — tracking des annonces lues par user.
 *
 * <p>Pattern : <i>PK composite (announcement_id, user_id) via {@code @IdClass} +
 * {@code @MapsId} sur les jointures</i>.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code @MapsId("announcementId")} → @Id announcementId dérivé de
 *       announcement.id ({@link TenantAnnouncement})</li>
 *   <li>{@code @MapsId("userId")} → @Id userId dérivé de user.id ({@link Profile})</li>
 * </ul>
 *
 * <p>Côté inverse {@code TenantAnnouncement} : <b>pas de {@code @OneToMany reads}</b>
 * — volume non borné (10k+ users par annonce). Repository paginé à la place.
 */
@Entity
@Table(name = "announcement_reads")
@IdClass(AnnouncementReadId.class)
public class AnnouncementRead {

    @Id
    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ─── Jointure announcement (PK partielle via @MapsId) ───────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("announcementId")
    @JoinColumn(name = "announcement_id", nullable = false, insertable = false, updatable = false)
    private TenantAnnouncement announcement;

    // ─── Jointure user (PK partielle via @MapsId) ───────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Profile user;

    @NotNull
    @Column(name = "body_version_read", nullable = false)
    private Integer bodyVersionRead;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected AnnouncementRead() {
        // JPA
    }

    public AnnouncementRead(TenantAnnouncement announcement, Profile user, Integer bodyVersionRead, Instant readAt) {
        this.announcement = announcement;
        this.user = user;
        this.bodyVersionRead = bodyVersionRead;
        this.readAt = readAt;
    }

    public UUID getAnnouncementId() { return announcementId; }
    public UUID getUserId() { return userId; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public TenantAnnouncement getAnnouncement() { return announcement; }
    public void setAnnouncement(TenantAnnouncement announcement) { this.announcement = announcement; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }

    public Integer getBodyVersionRead() { return bodyVersionRead; }
    public Instant getReadAt() { return readAt; }

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
        AnnouncementRead that = (AnnouncementRead) o;
        return Objects.equals(announcementId, that.announcementId)
            && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
