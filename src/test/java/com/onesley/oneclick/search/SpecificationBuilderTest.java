package com.onesley.oneclick.search;

import com.onesley.oneclick.exception.BadRequestException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link SpecificationBuilder} (L3 — search infra).
 * Whitelist eager + opérateurs (EQ/NEQ/LIKE/ILIKE/IN/BETWEEN/GT/GTE/LT/LTE/
 * IS_NULL/IS_NOT_NULL) + coercion de types (UUID/Number/enum/temporel/Boolean),
 * en invoquant la {@code toPredicate} avec des mocks JPA Criteria.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class SpecificationBuilderTest {

    enum Color { RED, BLUE }

    private Root root;
    private Path path;
    private CriteriaBuilder cb;
    private CriteriaQuery query;

    @BeforeEach
    void setup() {
        root = mock(Root.class);
        path = mock(Path.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(path.get(anyString())).thenReturn(path);
    }

    private void run(SearchOperator op, Object value, Class<?> javaType) {
        when(path.getJavaType()).thenReturn((Class) javaType);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", op, value)), Set.of("field"));
        spec.toPredicate(root, query, cb);
    }

    // ─── build() : whitelist + listes vides ─────────────────────────────────────

    @Test
    void build_nullOrEmpty_returnsNull() {
        assertThat(SpecificationBuilder.build(null, Set.of("a"))).isNull();
        assertThat(SpecificationBuilder.build(List.of(), Set.of("a"))).isNull();
    }

    @Test
    void build_fieldNotAllowed_throwsBadRequest_eagerly() {
        assertThatThrownBy(() -> SpecificationBuilder.build(
            List.of(new SearchCriterion("secret", SearchOperator.EQ, "x")), Set.of("field")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void build_nestedPath_resolvesSegments() {
        when(path.getJavaType()).thenReturn((Class) String.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("address.city", SearchOperator.EQ, "Casa")), Set.of("address.city"));
        spec.toPredicate(root, query, cb);
        verify(root).get("address");
        verify(path).get("city");
    }

    // ─── opérateurs ──────────────────────────────────────────────────────────

    @Test
    void eq_uuid_coercesAndEquals() {
        UUID id = UUID.randomUUID();
        run(SearchOperator.EQ, id.toString(), UUID.class);
        verify(cb).equal(eq(path), eq(id));
    }

    @Test
    void neq_string_passthrough() {
        run(SearchOperator.NEQ, "x", String.class);
        verify(cb).notEqual(eq(path), eq("x"));
    }

    @Test
    void like_callsLike() {
        run(SearchOperator.LIKE, "%abc%", String.class);
        verify(cb).like(any(Path.class), eq("%abc%"));
    }

    @Test
    void ilike_lowersAndLikes() {
        run(SearchOperator.ILIKE, "ABC", String.class);
        verify(cb).lower(any(Path.class));
        verify(cb).like(any(jakarta.persistence.criteria.Expression.class), eq("abc"));
    }

    @Test
    void in_list_coercesEachElement() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        run(SearchOperator.IN, List.of(a.toString(), b.toString()), UUID.class);
        verify(path).in(any(Collection.class));
    }

    @Test
    void in_nonList_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) UUID.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.IN, "not-a-list")), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void between_range_callsBetween() {
        run(SearchOperator.BETWEEN, List.of(1, 10), Integer.class);
        verify(cb).between(any(), eq(1), eq(10));
    }

    @Test
    void between_wrongSize_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) Integer.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.BETWEEN, List.of(1))), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void comparisonOperators_call_cb() {
        run(SearchOperator.GT, 5, Integer.class);
        verify(cb).greaterThan(any(), eq(5));
        run(SearchOperator.GTE, 5, Integer.class);
        verify(cb).greaterThanOrEqualTo(any(), eq(5));
        run(SearchOperator.LT, 5, Integer.class);
        verify(cb).lessThan(any(), eq(5));
        run(SearchOperator.LTE, 5, Integer.class);
        verify(cb).lessThanOrEqualTo(any(), eq(5));
    }

    @Test
    void isNull_andIsNotNull() {
        run(SearchOperator.IS_NULL, null, String.class);
        verify(cb).isNull(path);
        run(SearchOperator.IS_NOT_NULL, null, String.class);
        verify(cb).isNotNull(path);
    }

    // ─── coercion de types (via EQ) ──────────────────────────────────────────────

    @Test
    void coerce_numberTypes() {
        run(SearchOperator.EQ, 5, Integer.class);   verify(cb).equal(eq(path), eq(5));
        run(SearchOperator.EQ, 5L, Long.class);     verify(cb).equal(eq(path), eq(5L));
        run(SearchOperator.EQ, 5.5, Double.class);  verify(cb).equal(eq(path), eq(5.5));
        run(SearchOperator.EQ, 7, BigDecimal.class); verify(cb).equal(eq(path), eq(new BigDecimal("7")));
    }

    @Test
    void coerce_enumFromString() {
        run(SearchOperator.EQ, "RED", Color.class);
        verify(cb).equal(eq(path), eq(Color.RED));
    }

    @Test
    void coerce_temporalTypes() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        run(SearchOperator.EQ, "2026-01-01T10:00:00Z", Instant.class);
        verify(cb).equal(eq(path), eq(now));
        run(SearchOperator.EQ, "2026-05-21", LocalDate.class);
        verify(cb).equal(eq(path), eq(LocalDate.parse("2026-05-21")));
    }

    @Test
    void coerce_booleanFromString() {
        run(SearchOperator.EQ, "true", Boolean.class);
        verify(cb).equal(eq(path), eq(Boolean.TRUE));
    }

    @Test
    void coerce_invalidUuid_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) UUID.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.EQ, "not-a-uuid")), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void coerce_invalidInstant_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) Instant.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.GT, "pas-une-date")), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    // ─── coercion : branches restantes (null / LocalDateTime / invalides / fallback / non-Comparable) ───

    @Test
    void coerce_nullValue_returnsNull() {
        run(SearchOperator.EQ, null, String.class);
        verify(cb).equal(eq(path), eq((Object) null));
    }

    @Test
    void coerce_localDateTime_valid() {
        run(SearchOperator.EQ, "2026-01-01T10:00:00", java.time.LocalDateTime.class);
        verify(cb).equal(eq(path), eq(java.time.LocalDateTime.parse("2026-01-01T10:00:00")));
    }

    @Test
    void coerce_invalidLocalDate_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) LocalDate.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.EQ, "pas-une-date")), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void coerce_invalidLocalDateTime_throwsBadRequest() {
        when(path.getJavaType()).thenReturn((Class) java.time.LocalDateTime.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.EQ, "pas-une-date")), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void coerce_noMatchingRule_returnsValueAsIs() {
        // target Integer mais valeur String (pas un Number) → aucune règle ne matche → renvoie la valeur telle quelle
        run(SearchOperator.EQ, "abc", Integer.class);
        verify(cb).equal(eq(path), eq("abc"));
    }

    @Test
    void coerceComparable_nonComparable_throwsBadRequest() {
        // valeur déjà du bon type (Object) mais non Comparable → rejet sur opérateur d'ordre
        when(path.getJavaType()).thenReturn((Class) Object.class);
        Specification<Object> spec = SpecificationBuilder.build(
            List.of(new SearchCriterion("field", SearchOperator.GT, new Object())), Set.of("field"));
        assertThatThrownBy(() -> spec.toPredicate(root, query, cb)).isInstanceOf(BadRequestException.class);
    }
}
