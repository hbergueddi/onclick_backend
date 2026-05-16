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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * Mapping Google Places `types[]` → cuisine FR éditoriale.
     *
     * <p>Pattern senior : table read-only en mémoire (pas de table DB pour ça, c'est
     * un référentiel statique éditorial — ajouter une table = over-engineering YAGNI).
     * Si Google ajoute un type, on le mappe ici (1 ligne). Ordre = priorité quand
     * un resto a plusieurs types (le 1er match gagne).
     */
    private static final Map<String, String> TYPE_TO_CUISINE = new LinkedHashMap<>();
    static {
        TYPE_TO_CUISINE.put("moroccan_restaurant", "Marocaine");
        TYPE_TO_CUISINE.put("french_restaurant", "Française");
        TYPE_TO_CUISINE.put("italian_restaurant", "Italienne");
        TYPE_TO_CUISINE.put("japanese_restaurant", "Japonaise");
        TYPE_TO_CUISINE.put("chinese_restaurant", "Chinoise");
        TYPE_TO_CUISINE.put("indian_restaurant", "Indienne");
        TYPE_TO_CUISINE.put("thai_restaurant", "Thaïlandaise");
        TYPE_TO_CUISINE.put("lebanese_restaurant", "Libanaise");
        TYPE_TO_CUISINE.put("mediterranean_restaurant", "Méditerranéenne");
        TYPE_TO_CUISINE.put("spanish_restaurant", "Espagnole");
        TYPE_TO_CUISINE.put("greek_restaurant", "Grecque");
        TYPE_TO_CUISINE.put("turkish_restaurant", "Turque");
        TYPE_TO_CUISINE.put("american_restaurant", "Américaine");
        TYPE_TO_CUISINE.put("mexican_restaurant", "Mexicaine");
        TYPE_TO_CUISINE.put("vietnamese_restaurant", "Vietnamienne");
        TYPE_TO_CUISINE.put("korean_restaurant", "Coréenne");
        TYPE_TO_CUISINE.put("brazilian_restaurant", "Brésilienne");
        TYPE_TO_CUISINE.put("seafood_restaurant", "Poissons");
        TYPE_TO_CUISINE.put("sushi_restaurant", "Sushi");
        TYPE_TO_CUISINE.put("steak_house", "Grillades");
        TYPE_TO_CUISINE.put("pizza_restaurant", "Pizzeria");
        TYPE_TO_CUISINE.put("hamburger_restaurant", "Burger");
        TYPE_TO_CUISINE.put("fast_food_restaurant", "Fast Food");
        TYPE_TO_CUISINE.put("vegan_restaurant", "Végane");
        TYPE_TO_CUISINE.put("vegetarian_restaurant", "Végétarienne");
        TYPE_TO_CUISINE.put("breakfast_restaurant", "Petit-déj");
        TYPE_TO_CUISINE.put("brunch_restaurant", "Brunch");
        TYPE_TO_CUISINE.put("bakery", "Boulangerie");
        TYPE_TO_CUISINE.put("cafe", "Café");
        TYPE_TO_CUISINE.put("coffee_shop", "Café");
        TYPE_TO_CUISINE.put("bar", "Bar");
        TYPE_TO_CUISINE.put("ice_cream_shop", "Glacier");
        TYPE_TO_CUISINE.put("dessert_shop", "Desserts");
        TYPE_TO_CUISINE.put("restaurant", "Restaurant"); // fallback générique
    }

    /** Google `priceLevel` enum → notation €/€€/€€€/€€€€ (UX standard). */
    private static String mapPriceLevel(String priceLevel) {
        if (priceLevel == null) return null;
        return switch (priceLevel) {
            case "PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE" -> "€";
            case "PRICE_LEVEL_MODERATE" -> "€€";
            case "PRICE_LEVEL_EXPENSIVE" -> "€€€";
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> "€€€€";
            default -> null;
        };
    }

    /** Tag/cuisine générique Google sans valeur éditoriale — exclu des tags + cuisine si rien d'autre. */
    private static final String GENERIC_TYPE = "restaurant";
    private static final String GENERIC_LABEL_FR = "Restaurant";

    /**
     * Réduit le tableau `types[]` Google à la cuisine principale FR + une liste
     * de tags secondaires (max 3, sans la cuisine principale).
     *
     * <p>Règles métier :
     * <ul>
     *   <li><b>Type {@code "restaurant"} générique</b> : JAMAIS ajouté aux tags
     *       (doublon noise — "Restaurant" comme tag sur un resto = aucun signal).</li>
     *   <li><b>Cuisine</b> : si seul match = {@code "restaurant"} générique, retourne
     *       {@code null} → le frontend masque la ligne (cf cosmetic fix Compass).
     *       Évite d'écrire "Restaurant" comme cuisine dans la DB (revu V25).</li>
     * </ul>
     *
     * @return [cuisine, tags[]]
     */
    private static Object[] extractCuisineAndTags(JsonNode types) {
        if (types == null || !types.isArray() || types.isEmpty()) return new Object[]{null, new String[0]};
        String cuisine = null;
        List<String> tagsFR = new ArrayList<>();
        // Ordre TYPE_TO_CUISINE = priorité éditoriale (Marocaine > Française > … > Restaurant générique)
        outer:
        for (Map.Entry<String, String> entry : TYPE_TO_CUISINE.entrySet()) {
            for (JsonNode t : types) {
                if (entry.getKey().equals(t.asText())) {
                    if (cuisine == null) {
                        cuisine = entry.getValue();
                    } else if (tagsFR.size() < 3
                            && !tagsFR.contains(entry.getValue())
                            && !GENERIC_LABEL_FR.equals(entry.getValue())) {
                        // Ne jamais ajouter "Restaurant" aux tags — noise pur.
                        tagsFR.add(entry.getValue());
                    }
                    continue outer;
                }
            }
        }
        // Cuisine = "Restaurant" générique seul ⇒ NULL (front masque).
        if (GENERIC_LABEL_FR.equals(cuisine)) cuisine = null;
        return new Object[]{cuisine, tagsFR.toArray(new String[0])};
    }

    @Value("${app.google.places.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * RestClient construit depuis le {@code Builder} Spring auto-configuré :
     * il inclut {@code MappingJackson2HttpMessageConverter} avec l'ObjectMapper
     * global → désérialisation native JsonNode OK. Un {@code RestClient.builder()}
     * standalone n'a aucun converter → bug "Type definition error: JsonNode".
     */
    private final RestClient restClient;

    @PersistenceContext
    private EntityManager em;

    public GooglePlacesEnrichmentService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * Enrichit un restaurant à partir de Google Places.
     *
     * @param restaurantId UUID du resto
     * @param force        si true, force le refresh même si déjà enrichi
     * @return map résultat avec {enriched, skipped, error}
     */
    public Map<String, Object> enrichRestaurant(UUID restaurantId, boolean force) {
        Object[] resto = (Object[]) em.createNativeQuery("""
            SELECT id, name, city, google_updated_at, google_place_id
              FROM restaurants
             WHERE id = :id AND deleted_at IS NULL
            """).setParameter("id", restaurantId).getSingleResult();

        String name = (String) resto[1];
        String city = (String) resto[2];
        Object lastUpdate = resto[3];
        String existingPlaceId = (String) resto[4];

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
            //
            // OPTIMISATION : skip si on a déjà google_place_id en DB (cas backfill
            // --force sur restos déjà matchés). Économise ~50% du coût Google
            // (searchText = ~$0.032/call vs details = ~$0.017/call). En +force on
            // re-fetch UNIQUEMENT les details (data fraîches) sans refaire le match.
            //
            // NB : on récupère le body en String puis on parse avec l'ObjectMapper
            // local — sans dépendre des HttpMessageConverters du RestClient (qui
            // déclenchent "Type definition error: JsonNode" en Spring Boot 4
            // quand le Builder n'est pas explicitement configuré avec Jackson).
            String placeId;
            if (existingPlaceId != null && !existingPlaceId.isBlank()) {
                placeId = existingPlaceId;
                log.info("[places] reuse existing place_id for {} ({})", name, placeId);
            } else {
                String query = name + (city != null && !city.isBlank() ? " " + city + " Maroc" : "");
                String searchBody = restClient.post()
                    .uri(PLACES_TEXT_SEARCH)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "places.id,places.displayName")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(Map.of("textQuery", query, "languageCode", "fr")))
                    .retrieve()
                    .body(String.class);

                JsonNode searchResp = searchBody == null ? null : objectMapper.readTree(searchBody);
                if (searchResp == null || !searchResp.has("places") || searchResp.get("places").isEmpty()) {
                    log.info("[places] no match for {}", query);
                    return Map.of("enriched", false, "skipped", true, "reason", "no_match");
                }

                placeId = searchResp.get("places").get(0).get("id").asText();
            }

            // 2. Place details → hours, rating, GPS, phone, address, cuisine, budget
            //
            // FieldMask : `priceLevel` ajouté (mapping budget €/€€/€€€/€€€€), `types`
            // déjà présent — extraction cuisine FR + tags via TYPE_TO_CUISINE.
            String detailsBody = restClient.get()
                .uri(PLACES_DETAILS + placeId)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask",
                    "id,displayName,formattedAddress,nationalPhoneNumber,location," +
                    "rating,userRatingCount,regularOpeningHours,websiteUri,types,priceLevel")
                .retrieve()
                .body(String.class);

            JsonNode details = detailsBody == null ? null : objectMapper.readTree(detailsBody);
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

            // Cuisine + tags depuis `types[]`, budget depuis `priceLevel`. COALESCE en SQL
            // pour ne pas écraser une valeur éditoriale déjà saisie manuellement par un
            // owner (les chips PCC custom par ex.).
            Object[] cuisineAndTags = extractCuisineAndTags(details.get("types"));
            String cuisine = (String) cuisineAndTags[0];
            String[] tags = (String[]) cuisineAndTags[1];
            String budget = mapPriceLevel(details.has("priceLevel") ? details.get("priceLevel").asText() : null);

            em.createNativeQuery("""
                UPDATE restaurants
                   SET google_place_id = :placeId,
                       google_rating = COALESCE(:rating, google_rating),
                       google_reviews_count = COALESCE(:reviews, google_reviews_count),
                       opening_hours = COALESCE(CAST(:hours AS jsonb), opening_hours),
                       website_url = COALESCE(:website, website_url),
                       latitude = COALESCE(:lat, latitude),
                       longitude = COALESCE(:lng, longitude),
                       cuisine = COALESCE(cuisine, :cuisine),
                       budget = COALESCE(budget, :budget),
                       tags = COALESCE(NULLIF(tags, '{}'), :tags),
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
                .setParameter("cuisine", cuisine)
                .setParameter("budget", budget)
                .setParameter("tags", tags)
                .setParameter("id", restaurantId)
                .executeUpdate();

            log.info("[places] enriched {} place={} rating={} reviews={} cuisine={} budget={} tags={}",
                name, placeId, rating, reviewsCount, cuisine, budget, tags.length);
            return Map.of(
                "enriched", true,
                "skipped", false,
                "placeId", placeId,
                "rating", rating != null ? rating : "n/a",
                "reviewsCount", reviewsCount != null ? reviewsCount : 0,
                "cuisine", cuisine != null ? cuisine : "n/a",
                "budget", budget != null ? budget : "n/a"
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
