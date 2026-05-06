package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.announcement_reads} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : PK composite via @IdClass.
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

    @NotNull
    @Column(name = "body_version_read", nullable = false)
    private Integer bodyVersionRead;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected AnnouncementRead() {
        // JPA
    }

    public UUID getAnnouncementId() { return announcementId; }
    public UUID getUserId() { return userId; }
    public Integer getBodyVersionRead() { return bodyVersionRead; }
    public Instant getReadAt() { return readAt; }
}
