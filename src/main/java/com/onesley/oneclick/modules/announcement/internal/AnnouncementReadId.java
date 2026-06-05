package com.onesley.oneclick.modules.announcement.internal;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clé composite de {@link AnnouncementRead} — couple {@code (announcement_id, user_id)} (PK de la
 * table {@code announcement_reads}, V70).
 *
 * <p>Forme {@code @IdClass} (et non {@code @EmbeddedId}) : les deux champs sont des {@code @Id}
 * plats sur l'entité ({@code announcementId}/{@code userId}), cette classe ne sert qu'à matérialiser
 * la clé composite pour {@code JpaRepository<AnnouncementRead, AnnouncementReadId>}. Les noms des
 * champs DOIVENT correspondre exactement à ceux des {@code @Id} de {@link AnnouncementRead}.
 *
 * <p>{@code Serializable} + {@code equals}/{@code hashCode} par valeur : exigence JPA pour une clé
 * composite (identité de ligne fiable, usage en clé de cache/Map).</p>
 */
public class AnnouncementReadId implements Serializable {

    private UUID announcementId;
    private UUID userId;

    public AnnouncementReadId() {}

    public AnnouncementReadId(UUID announcementId, UUID userId) {
        this.announcementId = announcementId;
        this.userId = userId;
    }

    public UUID getAnnouncementId() {
        return announcementId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnouncementReadId that)) return false;
        return Objects.equals(announcementId, that.announcementId)
            && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(announcementId, userId);
    }
}
