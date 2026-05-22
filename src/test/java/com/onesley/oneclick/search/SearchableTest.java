package com.onesley.oneclick.search;

import com.onesley.oneclick.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link Searchable} (helper JPA recherche dynamique — pas d'Elasticsearch).
 * Couvre : critères vides vs présents, tri asc/desc/absent, champ de tri hors whitelist (400).
 */
class SearchableTest {

    private static final Set<String> ALLOWED = Set.of("name", "city");

    @SuppressWarnings("unchecked")
    private JpaSpecificationExecutor<Object> repoReturningEmpty() {
        JpaSpecificationExecutor<Object> repo = mock(JpaSpecificationExecutor.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        return repo;
    }

    @Test
    void execute_emptyCriteria_noSort() {
        var repo = repoReturningEmpty();
        var req = new SearchRequest(List.of(), null, 0, 20);
        assertThat(Searchable.execute(repo, req, ALLOWED, Function.identity()).getContent()).isEmpty();
    }

    @Test
    void execute_withCriteria_allowedField() {
        var repo = repoReturningEmpty();
        var req = new SearchRequest(List.of(new SearchCriterion("name", SearchOperator.EQ, "Casa")), null, 0, 20);
        assertThat(Searchable.execute(repo, req, ALLOWED, Function.identity()).getContent()).isEmpty();
    }

    @Test
    void execute_sortAsc_andDesc() {
        var repo = repoReturningEmpty();
        assertThat(Searchable.execute(repo, new SearchRequest(List.of(), "name,asc", 0, 20), ALLOWED, Function.identity()).getContent()).isEmpty();
        assertThat(Searchable.execute(repo, new SearchRequest(List.of(), "name,desc", 0, 20), ALLOWED, Function.identity()).getContent()).isEmpty();
        // tri sans direction explicite → ASC par défaut
        assertThat(Searchable.execute(repo, new SearchRequest(List.of(), "city", 0, 20), ALLOWED, Function.identity()).getContent()).isEmpty();
    }

    @Test
    void execute_sortFieldNotAllowed_throwsBadRequest() {
        var repo = repoReturningEmpty();
        var req = new SearchRequest(List.of(), "secret,asc", 0, 20);
        assertThatThrownBy(() -> Searchable.execute(repo, req, ALLOWED, Function.identity()))
            .isInstanceOf(BadRequestException.class);
    }
}
