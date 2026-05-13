package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ClientRatingRepository extends JpaRepository<ClientRating, UUID> {

    @Query("SELECT r FROM ClientRating r WHERE r.userId = :userId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<ClientRating> findByUser(UUID userId);

    @Query("SELECT AVG(r.visibleRating) FROM ClientRating r WHERE r.userId = :userId AND r.deletedAt IS NULL")
    Double averageVisibleRating(UUID userId);

    @Query("SELECT COUNT(r) FROM ClientRating r WHERE r.userId = :userId AND r.deletedAt IS NULL")
    Long countByUser(UUID userId);
}
