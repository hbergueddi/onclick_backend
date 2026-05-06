package com.onesley.oneclick.entity.tenant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clé composite pour {@link AnnouncementRead} (PK = announcement_id + user_id).
 * Généré par scripts/scaffold-jpa.mjs.
 */
public class AnnouncementReadId implements Serializable {

    private UUID announcementId;
    private UUID userId;

    public AnnouncementReadId() {
        // JPA
    }

    public AnnouncementReadId(UUID announcementId, UUID userId) {
        this.announcementId = announcementId;
        this.userId = userId;
    }

    public UUID getAnnouncementId() { return announcementId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnouncementReadId other)) return false;
        return Objects.equals(announcementId, other.announcementId)
            && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(announcementId, userId);
    }
}
