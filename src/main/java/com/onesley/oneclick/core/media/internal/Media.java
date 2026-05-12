package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Média polymorphique (image/video/audio/pdf) attaché à n'importe quelle entité.
 *
 * <p>Pattern : {@code entity_type + entity_id} pointent vers l'entité parente
 * (ex: {@code "restaurant" + restaurantId}). Pas de FK JPA — le service est
 * responsable de la cohérence (vérifier que l'entité existe).
 */
@Entity
@Table(name = "medias")
public class Media extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @NotBlank
    @Column(name = "url", nullable = false)
    private String url;

    @NotBlank
    @Pattern(regexp = "^(image|video|audio|pdf)$")
    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    protected Media() {
        // JPA
    }

    public Media(UUID id, String entityType, UUID entityId, String url, String mediaType) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.url = url;
        this.mediaType = mediaType;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMediaType() { return mediaType; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Map<String, Object> getMetadata() { return metadata; }

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
