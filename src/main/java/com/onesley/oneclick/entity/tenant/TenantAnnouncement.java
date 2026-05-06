package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.shared.AnnouncementPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.tenant_announcements} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "tenant_announcements")
public class TenantAnnouncement extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "priority", nullable = false, columnDefinition = "announcement_priority")
    private AnnouncementPriority priority;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Column(name = "publish_at", nullable = false)
    private Instant publishAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
    public UUID getTenantId() { return tenantId; }
    public UUID getAuthorId() { return authorId; }
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
}
