package com.onesley.oneclick.modules.promotion.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferReadRepository extends JpaRepository<OfferRead, UUID> {

    /** Ligne existante pour un couple (user, offer) — base de l'upsert idempotent. */
    Optional<OfferRead> findByUserIdAndOfferId(UUID userId, UUID offerId);

    /**
     * Ids des offres lues par un user (les plus récemment lues d'abord) — pour
     * piloter la logique « épinglées non-lues » côté front. Projection sur la
     * seule colonne {@code offer_id} : le front n'a besoin que des ids.
     */
    @Query("SELECT r.offerId FROM OfferRead r WHERE r.userId = :userId ORDER BY r.readAt DESC")
    List<UUID> findOfferIdsByUserId(@Param("userId") UUID userId);
}
