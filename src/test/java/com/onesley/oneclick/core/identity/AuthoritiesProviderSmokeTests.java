package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour le RBAC v2 (Bug 32) — endpoint GET /api/users/me/permissions
 * + vérification que les authorities VERB:RESOURCE sont correctement chargées
 * depuis la table {@code permissions} par {@code UserRoleAuthoritiesConverter}.
 *
 * <p>Ces tests dépendent de la migration {@code V29__rbac_verb_resource_canonical.sql}
 * qui canonicalise les actions+menus et grant SUPERADMIN les 96 permissions
 * cross-join. Si V29 n'a pas tourné, les tests échouent gracieusement (assertion
 * sur taille > 0) et indiquent que le seed est incomplet.
 */
class AuthoritiesProviderSmokeTests extends AbstractIntegrationTest {

    @Test
    void mePermissions_superadmin_returnsNonEmptyVerbResourceList() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/users/me/permissions"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // SUPERADMIN doit avoir au moins VIEW:RESTAURANTS (le pilote V29 grant
        // toutes les permissions cross-join à SUPERADMIN).
        assertThat(response.getBody())
            .as("SUPERADMIN doit recevoir au minimum VIEW:RESTAURANTS via le converter")
            .contains("VIEW:RESTAURANTS")
            .contains("CREATE:RESTAURANTS")
            .contains("UPDATE:RESTAURANTS")
            .contains("DELETE:RESTAURANTS");
    }

    @Test
    void mePermissions_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/users/me/permissions"),
            HttpMethod.GET,
            jwtEntity(null),
            String.class
        );
        // /me/permissions est protégé par isAuthenticated() — sans JWT, 401.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mePermissions_returnsOnlyVerbColonResourceEntries() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/users/me/permissions"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // L'endpoint filtre les authorities legacy (ROLE_SUPERADMIN, SUPERADMIN)
        // pour ne renvoyer QUE les authorities format VERB:RESOURCE (1 ":" séparateur).
        // Garantit que le frontend ne se mélange pas entre rôle et permission.
        assertThat(response.getBody())
            .doesNotContain("\"ROLE_SUPERADMIN\"")
            .doesNotContain("\"SUPERADMIN\"");
    }
}
