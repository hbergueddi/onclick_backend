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

    /** Un MEMBRE actif du tenant (slug) — via tenant_memberships (post-flip P3 : home oneclick). */
    private UUID memberOfTenant(String slug) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT tm.user_id::text FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
                + "WHERE t.slug = ? AND tm.status = 'active' AND tm.deleted_at IS NULL "
                + "ORDER BY tm.user_id LIMIT 1", String.class, slug));
    }

    /** Un CLIENT oneclick SANS aucune membership (vrai non-membre). */
    private UUID oneclickNonMember() {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM tenant_memberships tm WHERE tm.user_id = u.id AND tm.deleted_at IS NULL) "
                + "ORDER BY u.id LIMIT 1", String.class));
    }

    @Test
    void backfill_programClient_isActiveMemberOfHisProgram_notOfOneclick() {
        UUID palmeraie = tenantId("palmeraie");
        UUID oneclick = tenantId("oneclick");
        UUID user = memberOfTenant("palmeraie");

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
        UUID user = oneclickNonMember();
        UUID palmeraie = tenantId("palmeraie");

        assertThat(membership.isActiveMember(user, palmeraie)).isFalse();
        assertThat(membership.activeTenantIds(user)).doesNotContain(palmeraie);
    }

    @Test
    void backfill_producedActiveMemberships_towardsProgramsOnly() {
        // Post-flip P3 : les clients de programme ont désormais le home oneclick, donc on ne peut plus
        // dériver l'attendu de users.tenant_id. Le backfill V90 reste vérifiable par l'existence de
        // memberships actives, toutes orientées vers un tenant de PROGRAMME (jamais le socle oneclick).
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM tenant_memberships WHERE status = 'active' AND deleted_at IS NULL", Integer.class);
        assertThat(active).as("le backfill V90 a produit des memberships actives").isNotNull().isPositive();

        Integer towardsOneclick = jdbc.queryForObject(
                "SELECT count(*) FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
                + "WHERE t.slug = 'oneclick' AND tm.deleted_at IS NULL", Integer.class);
        assertThat(towardsOneclick).as("aucune membership vers le socle oneclick").isZero();
    }

    @Test
    void nullArgs_areSafe() {
        assertThat(membership.isActiveMember(null, null)).isFalse();
        assertThat(membership.activeMemberships(null)).isEmpty();
        assertThat(membership.activeTenantIds(null)).isEmpty();
    }
}
