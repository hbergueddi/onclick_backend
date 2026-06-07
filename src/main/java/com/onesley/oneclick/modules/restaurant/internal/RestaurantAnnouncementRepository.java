package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Repository {@link RestaurantAnnouncement} — annonce active (non expirée) par resto. */
@Repository
public interface RestaurantAnnouncementRepository extends JpaRepository<RestaurantAnnouncement, UUID> {

    /** Annonce active la plus récente d'un resto (expires_at > now). */
    Optional<RestaurantAnnouncement> findFirstByRestaurantIdAndExpiresAtAfterOrderByCreatedAtDesc(
        UUID restaurantId, Instant now);

    /**
     * Expire immédiatement toutes les annonces actives d'un resto sauf une (single-active).
     * Appelé après création pour garantir 1 seule annonce visible à la fois.
     */
    @Modifying
    @Query("UPDATE RestaurantAnnouncement a SET a.expiresAt = :now "
        + "WHERE a.restaurantId = :restaurantId AND a.id <> :exceptId AND a.expiresAt > :now")
    int expireOthers(@Param("restaurantId") UUID restaurantId,
                     @Param("exceptId") UUID exceptId,
                     @Param("now") Instant now);
}
