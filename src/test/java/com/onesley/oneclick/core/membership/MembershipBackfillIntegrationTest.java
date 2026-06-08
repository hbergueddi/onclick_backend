package com.onesley.oneclick.core.membership;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P0 — vérifie que la migration V90 (table + backfill miroir) et le port
 * {@link MembershipDirectoryApi} fonctionnent contre {@code oneclick_enterprise} :
 * un client d'un programme (palmeraie) est membre actif ; un client oneclick ne l'est pas.
 */
class MembershipBackfillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MembershipDirectoryApi membership;

    private UUID tenantId(String slug) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug));
    }

    private UUID clientOfTenant(String slug) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id JOIN tenants t ON t.id = u.tenant_id "
                + "WHERE t.slug = ? AND r.code = 'CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class, slug));
    }

    @Test
    void backfill_programClient_isActiveMemberOfHisProgram_notOfOneclick() {
        UUID palmeraie = tenantId("palmeraie");
        UUID oneclick = tenantId("oneclick");
        UUID user = clientOfTenant("palmeraie");

        assertThat(membership.isActiveMember(user, palmeraie))
                .as("client palmeraie = membre actif du programme palmeraie (backfill)").isTrue();
        assertThat(membership.isActiveMember(user, oneclick))
                .as("pas de membership vers le socle oneclick").isFalse();
        assertThat(membership.activeTenantIds(user)).contains(palmeraie);
        assertThat(membership.activeMemberships(user))
                .anySatisfy(m -> assertThat(m.tenantId()).isEqualTo(palmeraie));
    }

    @Test
    void backfill_pureOneclickClient_hasNoProgramMembership() {
        UUID user = clientOfTenant("oneclick");
        UUID palmeraie = tenantId("palmeraie");

        assertThat(membership.isActiveMember(user, palmeraie)).isFalse();
        assertThat(membership.activeTenantIds(user)).doesNotContain(palmeraie);
    }

    @Test
    void backfill_count_coversAllProgramClients() {
        Integer expected = jdbc.queryForObject(
                "SELECT count(*) FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.tenant_id IS NOT NULL "
                + "AND u.tenant_id NOT IN (SELECT id FROM tenants WHERE slug = 'oneclick')", Integer.class);
        Integer actual = jdbc.queryForObject(
                "SELECT count(*) FROM tenant_memberships WHERE status = 'active' AND deleted_at IS NULL", Integer.class);

        assertThat(expected).isNotNull().isPositive();
        // >= : d'éventuelles memberships créées par d'autres tests (P2) n'invalident pas le backfill.
        assertThat(actual).isGreaterThanOrEqualTo(expected);
    }

    @Test
    void nullArgs_areSafe() {
        assertThat(membership.isActiveMember(null, null)).isFalse();
        assertThat(membership.activeMemberships(null)).isEmpty();
        assertThat(membership.activeTenantIds(null)).isEmpty();
    }
}
