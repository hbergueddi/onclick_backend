package com.onesley.oneclick.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Set;
import java.util.function.Function;

/**
 * Helper pour endpoints {@code POST /api/{entity}/search} (Phase 4 spec senior §6.3).
 *
 * <p>Implémente le pattern :
 * <ol>
 *   <li>Reçoit un {@link SearchRequest} + une whitelist de champs autorisés</li>
 *   <li>Construit une {@link Specification} via {@link SpecificationBuilder}</li>
 *   <li>Exécute la recherche paginée avec tri optionnel</li>
 *   <li>Mappe vers le DTO de sortie</li>
 * </ol>
 *
 * <p>Sécurité : les champs hors whitelist déclenchent une {@code BadRequestException}
 * (400) gérée globalement via le {@code GlobalExceptionHandler}.
 */
public final class Searchable {

    private Searchable() {}

    /**
     * Recherche paginée + tri + mapping DTO en 1 appel.
     *
     * @param repo           Repo JpaSpecificationExecutor de l'entité
     * @param req            SearchRequest (JSON entrant)
     * @param allowedFields  Whitelist des champs filtrables/sortables
     * @param mapper         Fonction entity → DTO
     * @return Page<D> avec contenu mappé + métadonnées pagination
     */
    public static <E, D> Page<D> execute(
        JpaSpecificationExecutor<E> repo,
        SearchRequest req,
        Set<String> allowedFields,
        Function<E, D> mapper
    ) {
        Specification<E> spec = SpecificationBuilder.build(req.criteriaOrEmpty(), allowedFields);
        if (spec == null) {
            // Spec "always true" — JpaSpecificationExecutor n'a pas findAll(Pageable)
            spec = (root, query, cb) -> cb.conjunction();
        }
        Pageable pageable = buildPageable(req, allowedFields);
        return repo.findAll(spec, pageable).map(mapper);
    }

    /** Parse "field,asc|desc" → Sort, en validant le champ contre la whitelist. */
    private static Pageable buildPageable(SearchRequest req, Set<String> allowedFields) {
        Sort sort = Sort.unsorted();
        String s = req.sort();
        if (s != null && !s.isBlank()) {
            String[] parts = s.split(",");
            String field = parts[0].trim();
            if (!allowedFields.contains(field)) {
                throw new com.onesley.oneclick.exception.BadRequestException(
                    "sort field non autorisé : '" + field + "'. Autorisés : " + allowedFields
                );
            }
            Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(dir, field);
        }
        return PageRequest.of(req.pageOrZero(), req.sizeOrDefault(), sort);
    }
}
