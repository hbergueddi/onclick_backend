package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/** Commentaire sur un post. */
@Entity
@Table(name = "comments")
public class Comment extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "post_id", nullable = false, insertable = false, updatable = false) private UUID postId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false) private Post post;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "content", nullable = false) private String content;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Comment() {}
    public Comment(UUID id, Post post, User author, String content) {
        this.id = id; this.post = post; this.author = author; this.content = content;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public Post getPost() { return post; }
    public UUID getAuthorId() { return authorId; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getDeletedAt() { return deletedAt; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Comment) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
