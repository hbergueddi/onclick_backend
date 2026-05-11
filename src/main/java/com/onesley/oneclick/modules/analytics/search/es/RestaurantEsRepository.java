package com.onesley.oneclick.modules.analytics.search.es;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository Spring Data Elasticsearch — index {@code restaurants}.
 *
 * <p>Méthodes dérivées automatiquement :
 * <ul>
 *   <li>{@code findByCityAndActiveTrue(...)} : filtre par ville + actifs</li>
 *   <li>{@code findByNameContaining(...)} : recherche partielle nom</li>
 * </ul>
 *
 * <p>Pour les requêtes full-text avancées (boost, fuzzy, multi-field), passer
 * par {@code ElasticsearchOperations} directement dans le service.
 */
@Repository
public interface RestaurantEsRepository extends ElasticsearchRepository<RestaurantEsDoc, String> {

    Page<RestaurantEsDoc> findByCityAndStatus(String city, String status, Pageable pageable);

    Page<RestaurantEsDoc> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
