package com.onesley.oneclick.search;

import java.util.List;

/**
 * Requête de recherche dynamique côté API.
 *
 * <p>Exemple JSON :
 * <pre>
 * {
 *   "criteria": [
 *     {"field":"firstName", "op":"ILIKE", "value":"You%"},
 *     {"field":"reliabilityScore", "op":"GTE", "value":3.5}
 *   ],
 *   "sort": "reliabilityScore,desc",
 *   "page": 0,
 *   "size": 20
 * }
 * </pre>
 *
 * <p>Plusieurs critères = AND (intersection). Un futur {@code logical: "OR"}
 * pourra être ajouté si nécessaire.
 */
public record SearchRequest(
    List<SearchCriterion> criteria,
    String sort,
    Integer page,
    Integer size
) {
    public List<SearchCriterion> criteriaOrEmpty() {
        return criteria == null ? List.of() : criteria;
    }

    public int pageOrZero() {
        return page == null ? 0 : Math.max(0, page);
    }

    public int sizeOrDefault() {
        if (size == null) return 20;
        return Math.min(Math.max(1, size), 100); // bornes 1..100
    }
}
