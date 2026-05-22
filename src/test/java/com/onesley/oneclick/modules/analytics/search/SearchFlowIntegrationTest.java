package com.onesley.oneclick.modules.analytics.search;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 — {@code /api/search} : recherche full-text restaurants. PUBLIC (catalogue pré-login).
 *
 * <p>Elasticsearch est indisponible dans l'env de test → {@code searchRestaurants} bascule
 * sur le fallback {@code searchViaTsvector} (Postgres {@code plainto_tsquery('french', q)}).
 * On couvre les deux variantes : avec et sans filtre {@code city}.
 */
class SearchFlowIntegrationTest extends AbstractIntegrationTest {

    private String cityWithoutSpace() {
        return jdbc.queryForObject(
            "SELECT city FROM restaurants WHERE city IS NOT NULL AND city NOT LIKE '% %' AND deleted_at IS NULL LIMIT 1",
            String.class);
    }

    @Test
    void searchRestaurants_fullText_tsvectorFallback_200() {
        String city = cityWithoutSpace();
        // sans city → branche "pas de filtre ville"
        assertThat(restTemplate.exchange(url("/api/search/restaurants?q=cafe&limit=10"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        // avec city → branche "filtre ville"
        assertThat(restTemplate.exchange(url("/api/search/restaurants?q=resto&city=" + city + "&limit=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchByCity_200() {
        assertThat(restTemplate.exchange(url("/api/search/restaurants/by-city?city=" + cityWithoutSpace() + "&limit=5"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
