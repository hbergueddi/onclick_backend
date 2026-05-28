package com.onesley.oneclick.modules.promotion.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OfferImpressionRepository extends JpaRepository<OfferImpression, UUID> {

    /** Impressions depuis une date (fenêtre analytics admin), plus récentes d'abord. */
    List<OfferImpression> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);
}
