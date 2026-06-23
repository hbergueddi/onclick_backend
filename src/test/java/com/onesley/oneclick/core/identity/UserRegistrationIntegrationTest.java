package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration signup public — POST /api/users/register (permitAll) vs
 * POST /api/users (création admin, authentifié).
 *
 * <p>Régression visée : POST /api/users était hors permitAll → signup en 401,
 * et @NotNull roleId interdisait l'inscription sans rôle. Fix : endpoint public
 * /register (rôle CLIENT forcé serveur-side), /api/users reste protégé.
 *
 * <p>Cas SANS effet de bord (aucune écriture persistée) : on valide que
 * /register est PUBLIC (atteint la validation → 400, pas 401) et que /api/users
 * reste protégé (401 sans JWT). Le happy-path (201 + rôle CLIENT) est vérifié
 * en E2E curl (écrirait un user dans la DB dev partagée — non rejoué ici).
 */
class UserRegistrationIntegrationTest extends AbstractIntegrationTest {

    private int postJson(String path, String body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    // ─── /register est PUBLIC : atteint la validation (400), n'exige pas de JWT ──

    @Test
    void register_isPublic_shortPassword_returns400_not401() {
        // mot de passe 7 caractères < @Size(min=10) → 400. Un 401 signifierait que
        // l'endpoint n'est pas en permitAll (régression du fix signup).
        String body = "{\"email\":\"pub-" + java.util.UUID.randomUUID() + "@x.com\","
            + "\"password\":\"short12\",\"firstName\":\"A\",\"lastName\":\"B\"}";
        assertThat(postJson("/api/users/register", body, null)).isEqualTo(400);
    }

    @Test
    void register_isPublic_missingLastName_returns400_not401() {
        String body = "{\"email\":\"pub-" + java.util.UUID.randomUUID() + "@x.com\","
            + "\"password\":\"secret1234\",\"firstName\":\"A\"}";  // password valide (10) : 400 dû au lastName manquant
        assertThat(postJson("/api/users/register", body, null)).isEqualTo(400);
    }

    // ─── POST /api/users (création admin) reste PROTÉGÉ : 401 sans JWT ──────────

    @Test
    void adminCreate_noJwt_returns401() {
        // payload valide d'un UserCreateDto, mais sans bearer → rejeté au filtre (401)
        // avant la validation/controller : l'endpoint n'est PAS public.
        String body = "{\"roleId\":\"10000000-0000-0000-0000-000000000001\","
            + "\"email\":\"x@y.com\",\"password\":\"secret123\",\"firstName\":\"A\",\"lastName\":\"B\"}";
        assertThat(postJson("/api/users", body, null)).isEqualTo(401);
    }
}
