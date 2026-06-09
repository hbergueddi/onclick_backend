package com.onesley.oneclick.core.membership;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P1 — le PLIAGE des authorities de membership (modèle « l'invitation accorde les
 * permissions »).
 *
 * <p>P1 est <b>additif</b> : le rôle MEMBER (V91) octroie les authorities programme aux membres via
 * leur membership, et {@code OneClickUserDetailsService} les plie dans le security context. Le
 * durcissement (retrait des autorités du CLIENT global → non-membre 403) est SURGICAL et déféré
 * (P1.5, décision produit découverte vs membre-only ; V92 a restauré le CLIENT).
 *
 * <p>On teste donc la <b>source du pliage</b> ({@link MembershipDirectoryApi#authoritiesFor}) :
 * un membre palmeraie en obtient les autorités programme ; un client oneclick sans membership n'en
 * obtient aucune. + le chemin loader ({@code OneClickUserDetailsService}) expose bien l'autorité.
 */
class MembershipAuthorityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MembershipDirectoryApi membership;
    @Autowired private OneClickUserDetailsService userDetailsService;

    /**
     * Un utilisateur <b>MEMBRE actif</b> du tenant (slug), résolu via {@code tenant_memberships}
     * — indépendant du home tenant (post-flip P3 : les membres palmeraie ont le home oneclick).
     */
    private UUID memberOfTenant(String slug) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT tm.user_id::text FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
                + "WHERE t.slug = ? AND tm.status = 'active' AND tm.deleted_at IS NULL "
                + "ORDER BY tm.user_id LIMIT 1", String.class, slug));
    }

    /** Un CLIENT oneclick <b>sans aucune membership</b> (non-membre). */
    private UUID oneclickNonMember() {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM tenant_memberships tm "
                + "                WHERE tm.user_id = u.id AND tm.deleted_at IS NULL) "
                + "ORDER BY u.id LIMIT 1", String.class));
    }

    @Test
    void authoritiesFor_palmeraieMember_returnsProgramAuthorities() {
        Set<String> auth = membership.authoritiesFor(memberOfTenant("palmeraie"));
        assertThat(auth).as("authorities programme octroyées par la membership (rôle MEMBER)")
                .contains("VIEW:FAMILY", "VIEW:BOOKINGS");
    }

    @Test
    void authoritiesFor_oneclickClient_isEmpty_noMembership() {
        assertThat(membership.authoritiesFor(oneclickNonMember()))
                .as("client oneclick sans membership : aucune authority programme via membership").isEmpty();
    }

    @Test
    void loader_palmeraieMember_exposesProgramAuthority() {
        UUID uid = memberOfTenant("palmeraie");
        userDetailsService.evictUser(uid); // déterministe vs cache pré-migrations
        Set<String> auth = userDetailsService.loadUserByUsername(uid.toString()).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(auth).contains("VIEW:FAMILY", "CLIENT");
    }

    @Test
    void loader_oneclickClient_lacksMemberOnlyAuthority_butKeepsDiscoveryAndBase() {
        // Gate P1.5 : un client oneclick non-membre n'a PLUS les autorités membre-only (retirées du
        // CLIENT global par V93), mais GARDE la découverte (VIEW:RESOURCE_BOOKINGS) + son rôle de base.
        UUID uid = oneclickNonMember();
        userDetailsService.evictUser(uid);
        Set<String> auth = userDetailsService.loadUserByUsername(uid.toString()).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(auth).doesNotContain("VIEW:FAMILY", "CREATE:BOOKINGS", "VIEW:FEEDBACK");
        assertThat(auth).contains("VIEW:RESOURCE_BOOKINGS", "CLIENT");
    }
}
