package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.media.api.MediaDtos.FileDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fichier (PDF, docs) polymorphique attaché à une entité.
 *
 * <p>Pattern identique à {@link Media} mais séparé pour évoluer indépendamment.
 * Soft delete supporté ; pas de update_at car les fichiers sont immuables une fois uploadés.
 */
@Entity
@Table(name = "file_attachments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileAttachment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
     private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "path", nullable = false, length = 512)
     private String path;

    @Column(name = "mime_type", length = 64)
     private String mimeType;

    @Column(name = "size_bytes")
    @Setter private Long sizeBytes;

    @Column(name = "original_name", length = 128)
    @Setter private String originalName;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdBy;

    public FileAttachment(UUID id, String entityType, UUID entityId, String path, String mimeType) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.path = path;
        this.mimeType = mimeType;
    }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public FileDto toDto() {
        return new FileDto(id, entityType, entityId, path, mimeType, sizeBytes, originalName, createdAt, createdById);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        FileAttachment that = (FileAttachment) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
