package com.onesley.oneclick.modules.stories.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lecture d'une story par un utilisateur — table {@code pcc_story_views} (V87, Gap #7).
 *
 * <p>1 ligne par couple {@code (story_id, user_id)} (clé composite {@link PccStoryViewId}). Le
 * marquage est idempotent : on n'insère que si la vue n'existe pas (préserve {@code viewedAt}
 * d'origine = 1ère vue). Refs par {@code UUID} plats (pas de {@code @ManyToOne}) — convention du
 * module CLOSED, calquée sur {@code AnnouncementRead}.
 */
@Entity
@Table(name = "pcc_story_views")
@IdClass(PccStoryViewId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PccStoryView {

    @Id
    @Column(name = "story_id", nullable = false, updatable = false)
    private UUID storyId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    public PccStoryView(UUID storyId, UUID userId, Instant viewedAt) {
        this.storyId = storyId;
        this.userId = userId;
        this.viewedAt = viewedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        PccStoryView other = (PccStoryView) o;
        return Objects.equals(storyId, other.storyId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : Objects.hash(storyId, userId);
    }
}
