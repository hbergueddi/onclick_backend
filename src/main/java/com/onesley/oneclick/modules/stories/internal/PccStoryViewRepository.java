package com.onesley.oneclick.modules.stories.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository {@link PccStoryView} (Gap #7) — vues par couple (story_id, user_id). */
@Repository
public interface PccStoryViewRepository extends JpaRepository<PccStoryView, PccStoryViewId> {

    /** Idempotence du marquage : la vue existe-t-elle déjà ? */
    boolean existsByStoryIdAndUserId(UUID storyId, UUID userId);

    /**
     * Parmi un ensemble de stories, celles que {@code userId} a déjà vues.
     * Alimente l'enrichissement {@code viewed} du feed (ring unread/read).
     */
    @Query("SELECT v.storyId FROM PccStoryView v WHERE v.userId = :userId AND v.storyId IN :ids")
    List<UUID> findViewedStoryIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    /**
     * Nombre de vues par story, pour un ensemble de stories (stats staff/admin
     * « vue par X membres »). Renvoie {@code [storyId, count]} par ligne.
     */
    @Query("SELECT v.storyId, COUNT(v) FROM PccStoryView v WHERE v.storyId IN :ids GROUP BY v.storyId")
    List<Object[]> countByStoryIds(@Param("ids") Collection<UUID> ids);
}
