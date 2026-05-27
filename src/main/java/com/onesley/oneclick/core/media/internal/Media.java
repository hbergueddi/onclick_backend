package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Média polymorphique (image/video/audio/pdf) attaché à n'importe quelle entité.
 *
 * <p>Pattern : {@code entity_type + entity_id} pointent vers l'entité parente
 * (ex: {@code "restaurant" + restaurantId}). Pas de FK JPA — le service est
 * responsable de la cohérence (vérifier que l'entité existe).
 */
@Entity
@Table(name = "medias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
     private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "url", nullable = false, length = 512)
    @Setter private String url;

    @Column(name = "media_type", nullable = false, length = 64)
     private String mediaType;

    @Column(name = "mime_type", length = 64)
    @Setter private String mimeType;

    @Column(name = "size_bytes")
    @Setter private Long sizeBytes;

    @Column(name = "sort_order")
    @Setter private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    /** Statut de modération (V46) : pending | approved | rejected. Défaut approved. */
    @Column(name = "moderation_status", nullable = false)
    @Setter private String moderationStatus = "approved";

    public Media(UUID id, String entityType, UUID entityId, String url, String mediaType) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.url = url;
        this.mediaType = mediaType;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public MediaDto toDto() {
        return new MediaDto(id, entityType, entityId, url, mediaType, mimeType, sizeBytes, sortOrder,
            metadata, getCreatedAt());
    }

    /** Mapping vers le DTO de modération (V46). */
    public com.onesley.oneclick.core.media.api.MediaModerationDto toModerationDto() {
        return new com.onesley.oneclick.core.media.api.MediaModerationDto(
            id, entityType, entityId, mediaType, url, moderationStatus, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Media that = (Media) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
