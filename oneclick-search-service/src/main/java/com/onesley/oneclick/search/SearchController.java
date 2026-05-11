package com.onesley.oneclick.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Recherche full-text restaurants (Phase 2 §21 spec senior).
 *
 * <p>Utilise la colonne tsvector {@code restaurants.search_vector} (créée par
 * V3__module_restaurant.sql + trigger DB) avec {@code plainto_tsquery('french', q)}.
 *
 * <p>Pattern microservice : pas de JPA mapping vers {@code restaurants} (entité dans
 * oneclick-core). On utilise JdbcTemplate avec SQL natif pour lire les colonnes utiles
 * (id, name, city, cuisine, image). Les détails complets s'obtiennent via
 * {@code GET oneclick-core:/api/restaurants/{id}}.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Recherche full-text restaurants (Phase 2 §21)")
public class SearchController {

    private final JdbcTemplate jdbc;

    public SearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/restaurants")
    @Operation(summary = "Recherche restaurants par texte libre — ranking ts_rank tsvector")
    public List<Map<String, Object>> searchRestaurants(
        @RequestParam("q") String query,
        @RequestParam(value = "city", required = false) String city,
        @RequestParam(defaultValue = "20") int limit
    ) {
        // Use plainto_tsquery for safer parsing of user input
        StringBuilder sql = new StringBuilder("""
            SELECT r.id, r.name, r.city, r.address, r.phone,
                   ts_rank(rsd.document, plainto_tsquery('french', ?)) AS rank
            FROM restaurants r
            JOIN restaurant_search_documents rsd ON rsd.restaurant_id = r.id
            WHERE r.deleted_at IS NULL
              AND rsd.document @@ plainto_tsquery('french', ?)
            """);
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(query);
        params.add(query);
        if (city != null && !city.isBlank()) {
            sql.append(" AND r.city = ? ");
            params.add(city);
        }
        sql.append(" ORDER BY rank DESC LIMIT ?");
        params.add(limit);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/restaurants/by-city")
    @Operation(summary = "Liste rapide restaurants par ville (sans full-text)")
    public List<Map<String, Object>> searchByCity(
        @RequestParam("city") String city,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return jdbc.queryForList("""
            SELECT id, name, city, address, phone
            FROM restaurants
            WHERE city = ? AND deleted_at IS NULL
            ORDER BY name
            LIMIT ?
            """, city, limit);
    }
}
