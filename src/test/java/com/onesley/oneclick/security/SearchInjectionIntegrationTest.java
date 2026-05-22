package com.onesley.oneclick.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sécurité — anti-injection SQL sur la recherche dynamique ({@code POST /api/{entity}/search}).
 *
 * <p>Deux gardes prouvées de bout en bout (HTTP → DB réelle) :
 * <ol>
 *   <li><b>Whitelist de champs</b> : un nom de champ/tri hors {@code SEARCHABLE_FIELDS}
 *       (ex. payload {@code DROP TABLE}) est rejeté en 400 AVANT toute requête.</li>
 *   <li><b>Binding paramétré</b> : une <i>valeur</i> malveillante sur un champ autorisé
 *       est liée comme littéral JPA Criteria (jamais concaténée) → inerte (0 résultat,
 *       table intacte), pas d'exécution de la clause injectée.</li>
 * </ol>
 */
class SearchInjectionIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private static final String URL = "/api/restaurants/search";

    private ResponseEntity<String> search(Object criteria, String sort) {
        var body = new java.util.HashMap<String, Object>();
        body.put("criteria", criteria);
        if (sort != null) body.put("sort", sort);
        body.put("page", 0);
        body.put("size", 20);
        return restTemplate.exchange(url(URL), HttpMethod.POST, jsonJwtEntity(body, adminBearer()), String.class);
    }

    @Test
    void control_realCity_returnsRows() throws Exception {
        ResponseEntity<String> r = search(List.of(Map.of("field", "city", "op", "EQ", "value", "Casablanca")), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // l'engine fonctionne : au moins 1 resto à Casablanca dans le seed
        assertThat(om.readTree(r.getBody()).get("content").size()).isGreaterThan(0);
    }

    @Test
    void injection_orClause_inValue_boundAsLiteral_zeroRows() throws Exception {
        // si l'injection passait, "OR '1'='1'" renverrait TOUTE la table ; liée en littéral → 0
        ResponseEntity<String> r = search(
            List.of(Map.of("field", "city", "op", "EQ", "value", "Casablanca' OR '1'='1")), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(r.getBody()).get("content")).isEmpty();
    }

    @Test
    void injection_dropTableValue_inert_tableIntact() throws Exception {
        ResponseEntity<String> r = search(
            List.of(Map.of("field", "name", "op", "EQ", "value", "x'; DROP TABLE restaurants; --")), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(r.getBody()).get("content")).isEmpty();
        // la table existe toujours → aucune DDL exécutée
        assertThat(restTemplate.exchange(url("/api/restaurants?page=0&size=1"), HttpMethod.GET, jwtEntity(adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void injection_ilikePattern_boundSafely() throws Exception {
        ResponseEntity<String> r = search(
            List.of(Map.of("field", "name", "op", "ILIKE", "value", "%' OR 1=1 --")), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void injection_inFieldName_rejected_400() {
        ResponseEntity<String> r = search(
            List.of(Map.of("field", "name); DROP TABLE restaurants;--", "op", "EQ", "value", "x")), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void injection_inSortField_rejected_400() {
        ResponseEntity<String> r = search(List.of(), "name; DROP TABLE restaurants--,asc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
