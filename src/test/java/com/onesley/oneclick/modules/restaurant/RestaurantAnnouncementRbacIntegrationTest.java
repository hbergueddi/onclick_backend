package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration Gap #6 — annonce éphémère 24h par restaurant.
 *
 * <p>Contrat testé :
 * <ul>
 *   <li>GET annonce active = PUBLIC (sans JWT) — 204 si aucune (bannière fiche spotlight).</li>
 *   <li>POST en CLIENT → 403 (pas d'autorité UPDATE:RESTAURANTS).</li>
 *   <li>POST en admin → 201 + round-trip GET 200 + single-active (la 2e remplace la 1re).</li>
 *   <li>DELETE en admin → 204 + GET re-204.</li>
 * </ul>
 * L'ABAC fine « staff actif du resto » est couverte par le test unitaire (guard mocké) ;
 * l'admin (bypass) suffit ici pour valider le chemin d'écriture + le RBAC grossier.
 */
class RestaurantAnnouncementRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID restaurantId;

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants LIMIT 1", String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update("INSERT INTO restaurants (id, tenant_id, name, city) VALUES (?, ?, ?, ?)",
            restaurantId, tenantId, "GAP6-Resto-" + restaurantId, "Casablanca");
    }

    @AfterEach
    void cleanup() {
        if (restaurantId != null) {
            jdbc.update("DELETE FROM restaurant_announcements WHERE restaurant_id = ?", restaurantId);
            jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        }
        restaurantId = null;
    }

    @Test
    void getActive_public_noJwt_returns204WhenNone() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.GET, jwtEntity(null), String.class);
        // Public (pas de 401) + 204 car aucune annonce active.
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void publish_client_returns403() {
        int status = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.POST, jsonJwtEntity(Map.of("message", "Test"), bearerForRole("CLIENT")),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publish_admin_roundTrip_andSingleActive() {
        String admin = adminBearer();
        // 1re annonce
        ResponseEntity<Map> first = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.POST, jsonJwtEntity(Map.of("message", "Fermé dimanche"), admin), Map.class);
        assertThat(first.getStatusCode().value()).isEqualTo(201);

        // 2e annonce → remplace la 1re (single-active)
        ResponseEntity<Map> second = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.POST, jsonJwtEntity(Map.of("message", "Cuisine ouverte minuit"), admin), Map.class);
        assertThat(second.getStatusCode().value()).isEqualTo(201);

        // GET public renvoie la 2e (la plus récente, seule active)
        ResponseEntity<Map> get = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.GET, jwtEntity(null), Map.class);
        assertThat(get.getStatusCode().value()).isEqualTo(200);
        assertThat(get.getBody().get("message")).isEqualTo("Cuisine ouverte minuit");

        // DB : 1 seule annonce active (single-active enforce)
        Integer active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurant_announcements WHERE restaurant_id = ? AND expires_at > now()",
            Integer.class, restaurantId);
        assertThat(active).isEqualTo(1);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void delete_admin_removesAnnouncement() {
        String admin = adminBearer();
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.POST, jsonJwtEntity(Map.of("message", "À supprimer"), admin), Map.class);
        String id = (String) created.getBody().get("id");

        int del = restTemplate.exchange(
            url("/api/restaurants/announcements/" + id),
            HttpMethod.DELETE, jwtEntity(admin), Void.class).getStatusCode().value();
        assertThat(del).isEqualTo(204);

        // GET re-204 (plus d'annonce active)
        int get = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/announcement"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode().value();
        assertThat(get).isEqualTo(204);
    }
}
