package com.onesley.oneclick.security;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de l'adaptateur Spring Security {@link OneClickUserDetails}
 * (retour senior : « comme user, on cache le mot de passe ; c'est ici qu'on lie
 * rôle + menu + permissions »). Purs (zéro Spring/DB) : on construit le graphe
 * {@code User → Role → Permission(Menu, Action)} en mémoire.
 */
class OneClickUserDetailsTest {

    private static Action action(String code) { return new Action(UUID.randomUUID(), code, code, "core"); }
    private static Menu menu(String code) { return new Menu(UUID.randomUUID(), code, code); }

    private static Role roleWith(String code, Permission... perms) {
        Role r = new Role(UUID.randomUUID(), code, code);
        for (Permission p : perms) r.getPermissions().add(p);
        return r;
    }

    private static User userWith(Role role) {
        return new User(UUID.randomUUID(), role, "john@oneclick.ma", "$2a$10$abcdefghijklmnopqrstuv", "John", "Doe");
    }

    private static List<String> authStrings(OneClickUserDetails ud) {
        return ud.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void getPassword_isNull_passwordNeverExposed() {
        OneClickUserDetails ud = OneClickUserDetails.from(userWith(roleWith("CLIENT")));
        assertThat(ud.getPassword()).isNull();
    }

    @Test
    void getUsername_isUserUuid_matchesJwtSub() {
        User u = userWith(roleWith("CLIENT"));
        assertThat(OneClickUserDetails.from(u).getUsername()).isEqualTo(u.getId().toString());
    }

    @Test
    void getAuthorities_containsRolePrefixed_rolePlain_andVerbResource() {
        Role r = roleWith("STAFF");
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("RESERVATIONS"), action("VIEW")));
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("LOYALTY"), action("CREATE")));

        List<String> auths = authStrings(OneClickUserDetails.from(userWith(r)));

        assertThat(auths).contains("ROLE_STAFF", "STAFF", "VIEW:RESERVATIONS", "CREATE:LOYALTY");
    }

    @Test
    void getAuthorities_skipsPermissionsMissingActionOrMenu() {
        Role r = roleWith("STAFF");
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("DASHBOARD"), null)); // menu-only
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, null, action("VIEW")));     // action-only

        List<String> auths = authStrings(OneClickUserDetails.from(userWith(r)));

        assertThat(auths).containsExactlyInAnyOrder("ROLE_STAFF", "STAFF");
        assertThat(auths).noneMatch(a -> a.contains(":"));
    }

    @Test
    void flags_delegateToWrappedUser() {
        User u = userWith(roleWith("CLIENT"));
        OneClickUserDetails ud = OneClickUserDetails.from(u);

        assertThat(ud.isEnabled()).isTrue();
        assertThat(ud.isAccountNonLocked()).isTrue();
        assertThat(ud.isAccountNonExpired()).isTrue();
        assertThat(ud.isCredentialsNonExpired()).isTrue();

        // flip sur l'entité -> prouve la délégation (pas un `return true` en dur)
        ReflectionTestUtils.setField(u, "enabled", false);
        ReflectionTestUtils.setField(u, "accountNonLocked", false);
        assertThat(ud.isEnabled()).isFalse();
        assertThat(ud.isAccountNonLocked()).isFalse();
    }

    @Test
    void getUser_exposesWrappedEntity() {
        User u = userWith(roleWith("CLIENT"));
        assertThat(OneClickUserDetails.from(u).getUser()).isSameAs(u);
    }

    @Test
    void getAuthorities_mergesProgramAuthorities_fromMembership() {
        // P1 — les authorities octroyées par la membership s'additionnent au rôle de base.
        OneClickUserDetails ud = OneClickUserDetails.from(
            userWith(roleWith("CLIENT")), Set.of("VIEW:FAMILY", "CREATE:BOOKINGS"));

        assertThat(authStrings(ud)).contains("ROLE_CLIENT", "CLIENT", "VIEW:FAMILY", "CREATE:BOOKINGS");
    }

    @Test
    void getAuthorities_withoutMembership_hasNoProgramAuthority() {
        // CLIENT seul (sans membership) : pas d'accès programme (modèle hasAuthority strict).
        List<String> auths = authStrings(OneClickUserDetails.from(userWith(roleWith("CLIENT"))));

        assertThat(auths).contains("ROLE_CLIENT", "CLIENT");
        assertThat(auths).doesNotContain("VIEW:FAMILY", "CREATE:BOOKINGS");
    }

    @Test
    void constructor_rejectsNullUser() {
        assertThatThrownBy(() -> OneClickUserDetails.from(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsUserWithoutRole() {
        User noRole = new User(UUID.randomUUID(), null, "x@y.z", "h", "X", "Y");
        assertThatThrownBy(() -> OneClickUserDetails.from(noRole))
            .isInstanceOf(IllegalStateException.class);
    }
}
