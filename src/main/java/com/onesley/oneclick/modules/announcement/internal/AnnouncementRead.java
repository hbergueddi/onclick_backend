package com.onesley.oneclick.modules.announcement.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Acquittement d'une annonce par un utilisateur — table {@code announcement_reads} (V70).
 *
 * <p>1 ligne par couple {@code (announcement_id, user_id)} (clé composite {@link AnnouncementReadId}).
 * Le marquage est un upsert idempotent : re-marquer met à jour {@code readAt} et
 * {@code bodyVersionRead}, sans créer de doublon. L'édition du corps d'une annonce purge ces lignes
 * côté service (bump {@code body_version}) → l'utilisateur doit ré-acquitter la nouvelle version.
 *
 * <p>Refs par {@code UUID} plats (pas de {@code @ManyToOne} vers {@code Announcement}/{@code User}) :
 * le module {@code modules.announcement} est CLOSED — même convention que {@code OfferRead} (V63),
 * {@code OfferImpression} et {@code reservation_guests}.
 */
@Entity
@Table(name = "announcement_reads")
@IdClass(AnnouncementReadId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementRead {

    @Id
    @Column(name = "announcement_id", nullable = false, updatable = false)
    private UUID announcementId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Version du corps acquittée (= {@code Announcement.bodyVersion} au moment du mark-read). */
    @Column(name = "body_version_read", nullable = false)
    @Setter private int bodyVersionRead;

    @Column(name = "read_at", nullable = false)
    @Setter private Instant readAt;

    public AnnouncementRead(UUID announcementId, UUID userId, int bodyVersionRead, Instant readAt) {
        this.announcementId = announcementId;
        this.userId = userId;
        this.bodyVersionRead = bodyVersionRead;
        this.readAt = readAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        AnnouncementRead other = (AnnouncementRead) o;
        return Objects.equals(announcementId, other.announcementId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : Objects.hash(announcementId, userId);
    }
}
