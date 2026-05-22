package com.onesley.oneclick.integration;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — contrôleurs à dépendance externe (Search/ES, AI/Groq, Email/Resend, Auth).
 *
 * <p>On assert UNIQUEMENT les frontières déterministes (garde auth + mauvais identifiants),
 * pas les chemins « happy » qui appellent un service externe non disponible dans l'env de test
 * (Elasticsearch, Groq, Resend) — ceux-là relèvent de tests d'intégration externes.
 */
class InfraControllersAuthIntegrationTest extends AbstractIntegrationTest {

    // ─── Search (ES) ──────────────────────────────────────────────────────────
    @Test
    void search_adminReindex_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/search/admin/reindex"), HttpMethod.POST, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── AI (Groq) ────────────────────────────────────────────────────────────
    // Corps VALIDE (passe @Valid) + sans bearer → la garde de sécurité tranche → 401 stable.
    // (Avec corps invalide, @Valid lèverait 400 avant la garde — d'où les variantes 4xx ci-dessous.)
    @Test
    void ai_careChat_validBody_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/ai/care-chat"), HttpMethod.POST,
            jsonJwtEntity(Map.of("message", "bonjour"), null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ai_assistant_validBody_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/ai/assistant"), HttpMethod.POST,
            jsonJwtEntity(Map.of("prompt", "bonjour"), null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Email (Resend) ──────────────────────────────────────────────────────
    @Test
    void email_send_unauthenticated_4xx() {
        assertThat(restTemplate.exchange(url("/api/email/send"), HttpMethod.POST,
            jsonJwtEntity(Map.of("to", "x@y.ma"), null), String.class).getStatusCode().is4xxClientError()).isTrue();
    }

    // ─── Auth ────────────────────────────────────────────────────────────────
    @Test
    void auth_login_badCredentials_4xx() {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
            jsonJwtEntity(Map.of("email", "inconnu@x.ma", "password", "mauvais"), null), String.class);
        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void auth_login_invalidBody_400() {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
            jsonJwtEntity(Map.of(), null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void auth_refresh_invalid_4xx() {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/auth/refresh"), HttpMethod.POST,
            jsonJwtEntity(Map.of("refreshToken", "invalide"), null), String.class);
        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void auth_logout_unauthenticated_4xx() {
        // logout exige un corps + authentification → requête nue rejetée (4xx).
        assertThat(restTemplate.exchange(url("/api/auth/logout"), HttpMethod.POST, jwtEntity(null), String.class)
            .getStatusCode().is4xxClientError()).isTrue();
    }
}
