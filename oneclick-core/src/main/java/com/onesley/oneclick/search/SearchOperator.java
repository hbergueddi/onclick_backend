package com.onesley.oneclick.search;

/**
 * Opérateurs supportés par {@link SpecificationBuilder} pour les recherches
 * dynamiques.
 *
 * <p>Choix conservateur : on couvre les cas usuels (égalité, comparaison,
 * pattern matching, ranges, NULL). Les opérateurs textuels (LIKE/ILIKE)
 * supportent les wildcards SQL standard (% et _) directement dans la valeur.
 */
public enum SearchOperator {
    /** Égalité stricte (case-sensitive pour String). */
    EQ,
    /** Différent. */
    NEQ,
    /** Pattern matching case-sensitive (Postgres LIKE). */
    LIKE,
    /** Pattern matching case-insensitive (Postgres ILIKE). */
    ILIKE,
    /** Valeur dans une liste (la value doit être une List). */
    IN,
    /** Plage (la value doit être une List de 2 éléments [from, to]). */
    BETWEEN,
    /** Strictement supérieur. */
    GT,
    /** Supérieur ou égal. */
    GTE,
    /** Strictement inférieur. */
    LT,
    /** Inférieur ou égal. */
    LTE,
    /** Champ NULL (la value est ignorée). */
    IS_NULL,
    /** Champ NON NULL (la value est ignorée). */
    IS_NOT_NULL
}
