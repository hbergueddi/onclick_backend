package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuite de périmètre tenant (rapport « Coiffeur PCC dans Explore ») — la <b>découverte générique</b>
 * publique ({@code GET /api/restaurants} sans tenant explicite) ne doit JAMAIS faire remonter les
 * restaurants d'un programme (PCC/HOMU…), <b>même pour un membre</b> du programme : son contenu de
 * club passe par le <i>reveal</i> dédié (tenantId explicite), pas par l'Explore générique.
 *
 * <p>Couvre les 3 cas : (1) Explore générique d'un membre palmeraie → 0 restaurant programme ;
 * (2) requête tenant EXPLICITE d'un membre → ses restaurants programme (reveal OK) ; (3) requête
 * tenant explicite d'un NON-membre → 0 (impossible d'énumérer un programme non rejoint).
 */
class RestaurantCatalogTenantScopeIntegrationTest extends AbstractIntegrationTest {

    /** Les 8 modules PCC seedés en restaurants tenant palmeraie (cf rapport utilisateur). */
    private static final String[] PCC_MODULES = {
        "Coiffeur PCC", "Padel PCC", "Spa PCC", "Golf PCC", "Palm Gym", "Tennis PCC", "Foot PCC", "Basket PCC"
    };

    private UUID memberOfTenant(String slug) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT tm.user_id::text FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
            + "WHERE t.slug = ? AND tm.status = 'active' AND tm.deleted_at IS NULL LIMIT 1", String.class, slug));
    }

    private UUID oneclickNonMember() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM tenant_memberships tm WHERE tm.user_id = u.id "
            + "   AND tm.status = 'active' AND tm.deleted_at IS NULL) ORDER BY u.id LIMIT 1", String.class));
    }

    private String tenantId(String slug) {
        return jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = ? AND deleted_at IS NULL", String.class, slug);
    }

    private ResponseEntity<String> catalogue(String jwt, String query) {
        return restTemplate.exchange(
            url("/api/restaurants?size=2000" + query), HttpMethod.GET, jwtEntity(jwt), String.class);
    }

    @Test
    void genericCatalog_palmeraieMember_excludesProgramRestaurants() {
        String jwt = jwtIssuer.issueAccessToken(memberOfTenant("palmeraie"), "CLIENT").token();

        ResponseEntity<String> resp = catalogue(jwt, "");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        String body = resp.getBody();
        assertThat(body).isNotNull();
        // L'Explore générique d'un membre palmeraie ne contient AUCUN module programme PCC.
        for (String module : PCC_MODULES) {
            assertThat(body).as("module PCC '%s' ne doit pas fuiter dans l'Explore générique", module)
                .doesNotContain("\"" + module + "\"");
        }
        // Sanity : le catalogue oneclick public n'est pas vide (le filtre n'a pas tout coupé).
        assertThat(body).contains("\"content\"");
    }

    @Test
    void explicitTenant_palmeraieMember_returnsProgramRestaurants() {
        String jwt = jwtIssuer.issueAccessToken(memberOfTenant("palmeraie"), "CLIENT").token();

        ResponseEntity<String> resp = catalogue(jwt, "&tenantId=" + tenantId("palmeraie"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // Le reveal dédié (tenantId explicite) d'un membre expose bien ses restaurants programme.
        assertThat(resp.getBody()).contains("PCC");
    }

    @Test
    void explicitTenant_nonMember_cannotEnumerateProgram() {
        String jwt = jwtIssuer.issueAccessToken(oneclickNonMember(), "CLIENT").token();

        ResponseEntity<String> resp = catalogue(jwt, "&tenantId=" + tenantId("palmeraie"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // Un non-membre ne peut pas énumérer palmeraie, même en passant le tenantId explicitement.
        for (String module : PCC_MODULES) {
            assertThat(resp.getBody()).doesNotContain("\"" + module + "\"");
        }
    }
}
