package com.onesley.oneclick.modules.analytics.search;

import com.onesley.oneclick.modules.analytics.search.es.RestaurantEsDoc;
import com.onesley.oneclick.modules.analytics.search.es.RestaurantEsRepository;
import com.onesley.oneclick.modules.analytics.search.es.RestaurantEsSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Recherche full-text restaurants (Phase 2 §21 + Phase 3.4 §X spec senior).
 *
 * <p>Stratégie hybride :
 * <ul>
 *   <li><b>Primary</b> : Elasticsearch 7.17 (port 9200). Ranking BM25, fuzzy match,
 *       multi-field boost (name^3, description, address, city).</li>
 *   <li><b>Fallback</b> : tsvector PostgreSQL (colonne {@code restaurants.search_vector})
 *       avec {@code plainto_tsquery('french', q)} — utilisé si ES indispo ou disabled.</li>
 * </ul>
 *
 * <p>Toggle via {@code app.search.elasticsearch.enabled} (default true).
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Recherche full-text restaurants (Phase 3.4 — ES + tsvector fallback)")
@Slf4j
public class SearchController {

    private final JdbcTemplate jdbc;
    private final RestaurantEsRepository esRepo;
    private final ElasticsearchOperations esOps;
    private final RestaurantEsSyncService syncService;
    private final boolean esEnabled;

    public SearchController(JdbcTemplate jdbc,
                            RestaurantEsRepository esRepo,
                            ElasticsearchOperations esOps,
                            RestaurantEsSyncService syncService,
                            @Value("${app.search.elasticsearch.enabled:true}") boolean esEnabled) {
        this.jdbc = jdbc;
        this.esRepo = esRepo;
        this.esOps = esOps;
        this.syncService = syncService;
        this.esEnabled = esEnabled;
    }

    @GetMapping("/restaurants")
    @Operation(summary = "Recherche restaurants par texte libre — ES primary, tsvector fallback (PUBLIC)")
    // PUBLIC : recherche catalogue accessible pré-login (Login.tsx whitelabel picker).
    public List<Map<String, Object>> searchRestaurants(
        @RequestParam("q") String query,
        @RequestParam(value = "city", required = false) String city,
        @RequestParam(defaultValue = "20") int limit
    ) {
        // 1) Tenter Elasticsearch d'abord
        if (esEnabled) {
            try {
                return searchViaElasticsearch(query, city, limit);
            } catch (Exception e) {
                log.warn("ES search failed ({}). Falling back to tsvector.", e.getMessage());
            }
        }
        // 2) Fallback tsvector PostgreSQL
        return searchViaTsvector(query, city, limit);
    }

    /** ES query : multi_match name^3 + description + address + city, BM25 ranking. */
    private List<Map<String, Object>> searchViaElasticsearch(String query, String city, int limit) {
        Query q = Query.of(b -> b
            .bool(bb -> {
                bb.must(mm -> mm.multiMatch(m -> m
                    .query(query)
                    .fields("name^3", "description", "address", "city")
                    .fuzziness("AUTO")
                ));
                bb.filter(f -> f.term(t -> t.field("status").value("active")));
                if (city != null && !city.isBlank()) {
                    bb.filter(f -> f.term(t -> t.field("city").value(city)));
                }
                return bb;
            })
        );

        NativeQuery nq = NativeQuery.builder()
            .withQuery(q)
            .withPageable(PageRequest.of(0, limit))
            .build();

        SearchHits<RestaurantEsDoc> hits = esOps.search(nq, RestaurantEsDoc.class);
        List<Map<String, Object>> out = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<RestaurantEsDoc> h : hits.getSearchHits()) {
            RestaurantEsDoc d = h.getContent();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("name", d.getName());
            row.put("city", d.getCity());
            row.put("address", d.getAddress());
            row.put("phone", d.getPhone());
            row.put("status", d.getStatus());
            row.put("score", h.getScore());
            row.put("source", "elasticsearch");
            out.add(row);
        }
        return out;
    }

    /** Fallback : tsvector PostgreSQL avec ts_rank ranking. */
    private List<Map<String, Object>> searchViaTsvector(String query, String city, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT r.id, r.name, r.city, r.address, r.phone, r.status,
                   ts_rank(rsd.document, plainto_tsquery('french', ?)) AS rank,
                   'tsvector' AS source
            FROM restaurants r
            JOIN restaurant_search_documents rsd ON rsd.restaurant_id = r.id
            WHERE r.deleted_at IS NULL
              AND rsd.document @@ plainto_tsquery('french', ?)
            """);
        List<Object> params = new ArrayList<>();
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
    @Operation(summary = "Liste rapide restaurants par ville (sans full-text) (PUBLIC)")
    // PUBLIC : filtre rapide ville (Login.tsx picker resto par ville).
    public List<Map<String, Object>> searchByCity(
        @RequestParam("city") String city,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return jdbc.queryForList("""
            SELECT id, name, city, address, phone, status
            FROM restaurants
            WHERE city = ? AND deleted_at IS NULL
            ORDER BY name
            LIMIT ?
            """, city, limit);
    }

    @PostMapping("/admin/reindex")
    @Operation(summary = "Re-bulk-indexer toutes les restaurants vers Elasticsearch (admin)")
    @PreAuthorize("hasAuthority('UPDATE:ANALYTICS')")
    public Map<String, Object> reindex() {
        int count = syncService.bulkReindex();
        return Map.of("status", "ok", "indexed", count);
    }
}
