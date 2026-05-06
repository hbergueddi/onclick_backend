package com.onesley.oneclick.search;

import com.onesley.oneclick.exception.BadRequestException;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Transforme un {@link SearchRequest} en {@link Specification} JPA pour
 * exécution via {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}.
 *
 * <p>Sécurité : le builder exige une <b>whitelist de champs autorisés</b> par
 * appelant (typiquement le service métier qui sait quels champs sont safes à
 * exposer en recherche). Sans whitelist, {@link BadRequestException} est levée
 * — pas de scan arbitraire des colonnes sensibles ni d'injection via field name.
 *
 * <p>Coercion de types : les valeurs JSON arrivent en {@code String} pour les
 * UUID et les nombres, on parse à la volée selon le type Java de la colonne
 * cible (lu via la {@code Path<?>} JPA).
 */
public final class SpecificationBuilder {

    private SpecificationBuilder() {}

    /**
     * Construit une Specification à partir des critères, en validant chaque
     * field contre la whitelist.
     *
     * @param criteria       Liste des critères (AND entre eux)
     * @param allowedFields  Champs JPA autorisés (ex: "firstName", "city.name")
     * @param <T>            Type de l'entité
     * @return Specification combinée AND, ou {@code Specification.where(null)}
     *         si liste vide (matche tout)
     */
    public static <T> Specification<T> build(
        List<SearchCriterion> criteria, Set<String> allowedFields
    ) {
        if (criteria == null || criteria.isEmpty()) {
            return null;  // JpaSpecificationExecutor.findAll(null, page) = SELECT all
        }

        Specification<T> spec = Specification.unrestricted();
        for (SearchCriterion c : criteria) {
            spec = spec.and(toPredicate(c, allowedFields));
        }
        return spec;
    }

    private static <T> Specification<T> toPredicate(
        SearchCriterion c, Set<String> allowedFields
    ) {
        if (!allowedFields.contains(c.field())) {
            throw new BadRequestException(
                "Search field not allowed: " + c.field() +
                ". Allowed fields: " + allowedFields
            );
        }

        return (root, query, cb) -> {
            Path<?> path = resolvePath(root, c.field());
            Object value = c.value();

            return switch (c.op()) {
                case EQ        -> cb.equal(path, coerce(value, path.getJavaType()));
                case NEQ       -> cb.notEqual(path, coerce(value, path.getJavaType()));
                case LIKE      -> cb.like(asString(path), String.valueOf(value));
                case ILIKE     -> cb.like(cb.lower(asString(path)), String.valueOf(value).toLowerCase());
                case IN        -> path.in((List<?>) value);
                case BETWEEN   -> {
                    List<?> range = (List<?>) value;
                    if (range.size() != 2) {
                        throw new BadRequestException("BETWEEN expects [from, to]");
                    }
                    yield cb.between(
                        asComparable(path),
                        coerceComparable(range.get(0), path.getJavaType()),
                        coerceComparable(range.get(1), path.getJavaType())
                    );
                }
                case GT        -> cb.greaterThan(asComparable(path), coerceComparable(value, path.getJavaType()));
                case GTE       -> cb.greaterThanOrEqualTo(asComparable(path), coerceComparable(value, path.getJavaType()));
                case LT        -> cb.lessThan(asComparable(path), coerceComparable(value, path.getJavaType()));
                case LTE       -> cb.lessThanOrEqualTo(asComparable(path), coerceComparable(value, path.getJavaType()));
                case IS_NULL   -> cb.isNull(path);
                case IS_NOT_NULL -> cb.isNotNull(path);
            };
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Path<T> resolvePath(jakarta.persistence.criteria.Root<?> root, String field) {
        // Support nested paths : "address.city" → root.get("address").get("city")
        Path<?> path = root;
        for (String segment : field.split("\\.")) {
            path = path.get(segment);
        }
        return (Path<T>) path;
    }

    @SuppressWarnings("unchecked")
    private static <T> Path<String> asString(Path<T> path) {
        return (Path<String>) path;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Path asComparable(Path<?> path) {
        return (Path) path;
    }

    /**
     * Coerce une valeur JSON ({@code String}/{@code Number}/{@code Boolean})
     * vers le type Java de la colonne cible.
     */
    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == UUID.class && value instanceof String s) {
            try { return UUID.fromString(s); }
            catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid UUID: " + s);
            }
        }
        if (targetType == Integer.class && value instanceof Number n) return n.intValue();
        if (targetType == Long.class && value instanceof Number n) return n.longValue();
        if (targetType == Double.class && value instanceof Number n) return n.doubleValue();
        if (targetType == java.math.BigDecimal.class && value instanceof Number n) {
            return new java.math.BigDecimal(n.toString());
        }
        if (targetType.isEnum() && value instanceof String s) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object e = Enum.valueOf((Class<Enum>) targetType, s);
            return e;
        }
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable coerceComparable(Object value, Class<?> targetType) {
        Object coerced = coerce(value, targetType);
        if (coerced instanceof Comparable c) return c;
        throw new BadRequestException(
            "Value " + value + " (target " + targetType.getSimpleName() +
            ") is not Comparable for range/comparison operators"
        );
    }
}
