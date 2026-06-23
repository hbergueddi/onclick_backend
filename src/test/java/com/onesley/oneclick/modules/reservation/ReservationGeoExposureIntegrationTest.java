package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration — la géo restaurant (adresse + coordonnées) descend bien dans la lecture
 * enrichie {@code GET /api/reservations} (projection {@code findAllWithJoins}).
 *
 * <p>C'est le contrat qui alimente le bouton « Y aller » (Google Maps) sur chaque
 * réservation côté clients (web/iOS/Android), invités compris. Valide les nouveaux
 * alias SQL ({@code restaurantAddress / restaurantLatitude / restaurantLongitude /
 * restaurantGooglePlaceId}) de bout en bout sur le schéma réel.</p>
 */
class ReservationGeoExposureIntegrationTest extends AbstractIntegrationTest {

    private UUID restaurantId;
    private UUID reservationId;

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'oneclick' AND deleted_at IS NULL LIMIT 1", String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO restaurants (id, tenant_id, name, city, address, latitude, longitude, google_place_id)
            VALUES (?, ?, ?, 'Casablanca', '12 Rue Test, Casablanca', 33.5731000, -7.5898000, 'ChIJgeoTest')
            """, restaurantId, tenantId, "GEO-Resto-" + restaurantId);
        reservationId = UUID.randomUUID();
        // Créneau futur (le trigger interdit le passé) ; client = SUPERADMIN seedé.
        jdbc.update("""
            INSERT INTO reservations (id, tenant_id, client_id, restaurant_id, reservation_at, guest_count, status)
            VALUES (?, ?, ?, ?, NOW() + INTERVAL '2 days', 2, 'confirmed')
            """, reservationId, tenantId, SEED_SUPERADMIN_ID, restaurantId);
    }

    @AfterEach
    void cleanup() {
        if (reservationId != null) jdbc.update("DELETE FROM reservations WHERE id = ?", reservationId);
        if (restaurantId != null) jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        reservationId = null;
        restaurantId = null;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enrichedList_exposesRestaurantGeoForMapsDeepLink() {
        ResponseEntity<Map> resp = restTemplate.exchange(
            url("/api/reservations?restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(adminBearer()), Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(content).as("la réservation enrichie du resto ciblé").isNotEmpty();

        Map<String, Object> row = content.stream()
            .filter(r -> reservationId.toString().equals(String.valueOf(r.get("id"))))
            .findFirst().orElseThrow();

        // Les 4 champs géo alimentant le deep-link Google Maps sont bien projetés.
        assertThat(row.get("restaurantAddress")).isEqualTo("12 Rue Test, Casablanca");
        assertThat(row.get("restaurantLatitude")).isNotNull();
        assertThat(row.get("restaurantLongitude")).isNotNull();
        assertThat(row.get("restaurantGooglePlaceId")).isEqualTo("ChIJgeoTest");
    }
}
