package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Post communauté (feed social). */
@Entity
@Table(name = "posts")
public class Post extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "content", nullable = false) private String content;
    @Pattern(regexp = "^(public|friends|private)$") @Column(name = "visibility", nullable = false) private String visibility = "public";
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Post() {}
    public Post(UUID id, User author, String content) { this.id = id; this.author = author; this.content = content; }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Instant getDeletedAt() { return deletedAt; }
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
