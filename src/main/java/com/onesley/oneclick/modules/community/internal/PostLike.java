package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostLikeDto;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Like sur un post. UNIQUE (post_id, user_id). */
@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "post_id", nullable = false, insertable = false, updatable = false) private UUID postId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false) private Post post;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    public PostLike(UUID id, Post post, User user) { this.id = id; this.post = post; this.user = user; }

    /** Mapping vers le DTO public exposé hors du module. */
    public PostLikeDto toDto() {
        return new PostLikeDto(id, postId, userId, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((PostLike) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
