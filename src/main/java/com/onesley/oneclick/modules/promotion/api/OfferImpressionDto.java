package com.onesley.oneclick.modules.promotion.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue d'une offre par un client (tracking) — table {@code offer_impressions} (V21).
 * Alimente le dashboard exécutif Promotions (vues / viewers uniques / conversion).
 */
public record OfferImpressionDto(
    UUID offerId,
    UUID userId,
    String impressionType,
    Instant createdAt
) {}
