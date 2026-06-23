package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-4 — {@code GET /api/places/search} : endpoint d'autocomplétion Google Places <b>public</b>
 * (formulaire d'inscription resto, candidat non authentifié).
 *
 * <p>En contexte de test la clé Google n'est pas configurée → réponse = tableau JSON vide (stub),
 * mais l'important ici est l'accès public (pas de 401) + la validation du paramètre requis.
 */
class PlacesSearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void search_public_noAuth_returns200_jsonArray() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/places/search?q=Bistrot&country=Maroc"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).startsWith("[");  // tableau JSON (vide en test — pas de clé Google)
    }

    @Test
    void search_missingQuery_400() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/places/search"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
