package com.onesley.oneclick.modules.restaurant.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service Sprint I.3 — Enrichissement Google Places (port EF fetch-google-places).
 *
 * <p>Pattern senior : ToS Google compliant (option C) — pas de stockage des photos
 * Google (interdit section 10.5), uniquement les data (rating, hours, address, GPS).
 *
 * <p>Stub mode si {@code app.google.places.api-key} absent : retourne les data
 * déjà en DB sans appel HTTP, ou un message explicite.
 */
@Service
@Transactional
public class GooglePlacesEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesEnrichmentService.class);
    private static final String PLACES_TEXT_SEARCH = "https://places.googleapis.com/v1/places:searchText";
    private static final String PLACES_DETAILS = "https://places.googleapis.com/v1/places/";

    @Value("${app.google.places.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder().build();

    @PersistenceContext
    private EntityManager em;

    /**
     * Enrichit un restaurant à partir de Google Places.
     *
     * @param restaurantId UUID du resto
     * @param force        si true, force le refresh même si déjà enrichi
     * @return map résultat avec {enriched, skipped, error}
     */
    public Map<String, Object> enrichRestaurant(UUID restaurantId, boolean force) {
        Object[] resto = (Object[]) em.createNativeQuery("""
            SELECT id, name, city, google_updated_at
              FROM restaurants
             WHERE id = :id AND deleted_at IS NULL
            """).setParameter("id", restaurantId).getSingleResult();

        String name = (String) resto[1];
        String city = (String) resto[2];
        Object lastUpdate = resto[3];

        if (!force && lastUpdate != null) {
            log.info("[places] skip {} — already enriched at {}", name, lastUpdate);
            return Map.of("enriched", false, "skipped", true, "reason", "already_enriched");
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[places] api-key absent — stub mode, no enrichment");
            return Map.of("enriched", false, "skipped", true, "reason", "no_api_key");
        }

        try {
            // 1. Text search → place_id
            String query = name + (city != null && !city.isBlank() ? " " + city + " Maroc" : "");
            JsonNode searchResp = restClient.post()
                .uri(PLACES_TEXT_SEARCH)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "places.id,places.displayName")
                .header("Content-Type", "application/json")
                .body(Map.of("textQuery", query, "languageCode", "fr"))
                .retrieve()
                .body(JsonNode.class);

            if (searchResp == null || !searchResp.has("places") || searchResp.get("places").isEmpty()) {
                log.info("[places] no match for {}", query);
                return Map.of("enriched", false, "skipped", true, "reason", "no_match");
            }

            String placeId = searchResp.get("places").get(0).get("id").asText();

            // 2. Place details → hours, rating, GPS, phone, address
            JsonNode details = restClient.get()
                .uri(PLACES_DETAILS + placeId)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask",
                    "id,displayName,formattedAddress,nationalPhoneNumber,location," +
                    "rating,userRatingCount,regularOpeningHours,websiteUri,types")
                .retrieve()
                .body(JsonNode.class);

            if (details == null) {
                return Map.of("enriched", false, "skipped", true, "reason", "no_details");
            }

            // 3. Update restaurant row
            BigDecimal rating = details.has("rating") ? new BigDecimal(details.get("rating").asText()) : null;
            Integer reviewsCount = details.has("userRatingCount") ? details.get("userRatingCount").asInt() : null;
            String address = details.has("formattedAddress") ? details.get("formattedAddress").asText() : null;
            String phone = details.has("nationalPhoneNumber") ? details.get("nationalPhoneNumber").asText() : null;
            String website = details.has("websiteUri") ? details.get("websiteUri").asText() : null;
            Double lat = details.has("location") ? details.get("location").get("latitude").asDouble() : null;
            Double lng = details.has("location") ? details.get("location").get("longitude").asDouble() : null;
            String openingHoursJson = details.has("regularOpeningHours")
                ? details.get("regularOpeningHours").toString()
                : null;

            em.createNativeQuery("""
                UPDATE restaurants
                   SET google_place_id = :placeId,
                       google_rating = COALESCE(:rating, google_rating),
                       google_reviews_count = COALESCE(:reviews, google_reviews_count),
                       opening_hours = COALESCE(:hours::jsonb, opening_hours),
                       website_url = COALESCE(:website, website_url),
                       latitude = COALESCE(:lat, latitude),
                       longitude = COALESCE(:lng, longitude),
                       google_updated_at = NOW(),
                       updated_at = NOW()
                 WHERE id = :id
                """)
                .setParameter("placeId", placeId)
                .setParameter("rating", rating)
                .setParameter("reviews", reviewsCount)
                .setParameter("hours", openingHoursJson)
                .setParameter("website", website)
                .setParameter("lat", lat)
                .setParameter("lng", lng)
                .setParameter("id", restaurantId)
                .executeUpdate();

            log.info("[places] enriched {} place={} rating={} reviews={}",
                name, placeId, rating, reviewsCount);
            return Map.of(
                "enriched", true,
                "skipped", false,
                "placeId", placeId,
                "rating", rating != null ? rating : "n/a",
                "reviewsCount", reviewsCount != null ? reviewsCount : 0
            );

        } catch (Exception e) {
            log.error("[places] enrichment failed for {}: {}", name, e.getMessage());
            return Map.of(
                "enriched", false,
                "skipped", false,
                "error", e.getMessage()
            );
        }
    }
}
