package com.onesley.oneclick.security;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la refonte {@link OneClickUserDetails} (« comme user, mdp caché ») :
 * round-trip Redis (miss → write → hit → read) avec profil présent, mot de passe
 * caché, et rôle/menus/permissions liés.
 */
class OneClickUserDetailsCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OneClickUserDetailsService service;

    @Test
    void userDetails_roundTripsThroughRedis_withProfile_andHiddenPassword() {
        String username = SEED_SUPERADMIN_ID.toString();
        service.evictUser(SEED_SUPERADMIN_ID); // force un cache MISS sur le 1er appel

        OneClickUserDetails fromDb    = (OneClickUserDetails) service.loadUserByUsername(username); // MISS → write Redis
        OneClickUserDetails fromCache = (OneClickUserDetails) service.loadUserByUsername(username); // HIT  → read Redis

        for (OneClickUserDetails ud : new OneClickUserDetails[]{fromDb, fromCache}) {
            // « comme user » : le profil complet est porté
            assertThat(ud.getUser()).isNotNull();
            assertThat(ud.getUser().getEmail()).isNotBlank();
            assertThat(ud.getUser().getFirstName()).isNotNull();
            // rôle + permissions liés → authorities calculées
            assertThat(ud.getUser().getRole().getCode()).isNotBlank();
            assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().startsWith("ROLE_"));
            // mot de passe caché
            assertThat(ud.getPassword()).isNull();
            assertThat(ud.getUsername()).isEqualTo(username);
        }
    }
}
