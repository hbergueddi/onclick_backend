package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostDto;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Post communauté (feed social). */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
     @Column(name = "content", nullable = false, length = 4096) @Setter private String content;
     @Column(name = "visibility", nullable = false, length = 64) @Setter private String visibility = "public";
    @Column(name = "deleted_at") private Instant deletedAt;
    public Post(UUID id, User author, String content) { this.id = id; this.author = author; this.content = content; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public PostDto toDto() {
        return new PostDto(id, authorId, content, visibility, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Post) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
