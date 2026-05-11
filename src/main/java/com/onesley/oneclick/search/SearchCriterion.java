package com.onesley.oneclick.search;

/**
 * Un critère de recherche atomique.
 *
 * <p>Format JSON attendu côté API REST :
 * <pre>
 * {
 *   "field":     "firstName",
 *   "op":        "ILIKE",
 *   "value":     "You%"
 * }
 * </pre>
 *
 * <p>Pour les opérateurs sans valeur ({@code IS_NULL}, {@code IS_NOT_NULL}),
 * le champ {@code value} est ignoré.
 *
 * <p>Pour {@code IN}, {@code value} est une liste : {@code [v1, v2, ...]}.
 * Pour {@code BETWEEN}, {@code value} est {@code [from, to]}.
 *
 * <p><b>Sécurité</b> : le {@link SpecificationBuilder} valide le {@code field}
 * contre une whitelist par entité (pas de risque d'injection ni de scan
 * arbitraire de colonnes sensibles).
 */
public record SearchCriterion(
    String field,
    SearchOperator op,
    Object value
) {
}
