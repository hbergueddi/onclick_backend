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

    private UUID clientOfTenant(String slug) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id JOIN tenants t ON t.id = u.tenant_id "
                + "WHERE t.slug = ? AND r.code = 'CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class, slug));
    }

    @Test
    void authoritiesFor_palmeraieMember_returnsProgramAuthorities() {
        Set<String> auth = membership.authoritiesFor(clientOfTenant("palmeraie"));
        assertThat(auth).as("authorities programme octroyées par la membership (rôle MEMBER)")
                .contains("VIEW:FAMILY", "VIEW:BOOKINGS");
    }

    @Test
    void authoritiesFor_oneclickClient_isEmpty_noMembership() {
        assertThat(membership.authoritiesFor(clientOfTenant("oneclick")))
                .as("client oneclick sans membership : aucune authority programme via membership").isEmpty();
    }

    @Test
    void loader_palmeraieMember_exposesProgramAuthority() {
        UUID uid = clientOfTenant("palmeraie");
        userDetailsService.evictUser(uid); // déterministe vs cache pré-V91/V92
        Set<String> auth = userDetailsService.loadUserByUsername(uid.toString()).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(auth).contains("VIEW:FAMILY", "CLIENT");
    }
}
