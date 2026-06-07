package com.onesley.oneclick.modules.stories.internal;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clé composite de {@link PccStoryView} — couple {@code (story_id, user_id)} (PK de
 * {@code pcc_story_views}, V87). Forme {@code @IdClass} (champs {@code storyId}/{@code userId} plats
 * sur l'entité). {@code Serializable} + equals/hashCode par valeur : exigence JPA.
 */
public class PccStoryViewId implements Serializable {

    private UUID storyId;
    private UUID userId;

    public PccStoryViewId() {}

    public PccStoryViewId(UUID storyId, UUID userId) {
        this.storyId = storyId;
        this.userId = userId;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PccStoryViewId that)) return false;
        return Objects.equals(storyId, that.storyId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyId, userId);
    }
}
