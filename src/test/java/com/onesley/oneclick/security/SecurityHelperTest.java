package com.onesley.oneclick.security;

import com.onesley.oneclick.exception.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests unitaires isolés du {@link SecurityHelper} (audit R4, directive #2).
 *
 * <p>Vérifie que les discriminants de portée sont 100 % basés sur des AUTORITÉS (jamais sur
 * un rôle) : {@code isAdmin()} == {@code hasAuthority("VIEW:USERS")} (admins seulement) et
 * {@code isStaffOrAdmin()} == {@code hasAuthority("VIEW:STAFF")} (population gestion). Couvre
 * aussi le bypass admin de {@link SecurityHelper#requireOwnerOrAdmin(UUID)}.
 */
class SecurityHelperTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Pose une authentification porteuse des autorités données (sans identité JWT). */
    private void authWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("u", "p", authorities));
    }

    /** Pose une authentification JWT (sub = userId) + autorités. */
    private void jwtAuth(UUID userId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .subject(userId.toString()).claim("scope", "test").build();
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, granted));
    }

    @Test
    void isAdmin_trueOnlyWithViewUsers() {
        authWith("VIEW:USERS");
        assertThat(SecurityHelper.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_falseWithoutViewUsers() {
        authWith("VIEW:STAFF", "VIEW:RESERVATIONS"); // staff/resto, pas admin
        assertThat(SecurityHelper.isAdmin()).isFalse();
    }

    @Test
    void isAdmin_falseWhenAnonymous() {
        assertThat(SecurityHelper.isAdmin()).isFalse();
    }

    @Test
    void isStaffOrAdmin_trueWithViewStaff() {
        authWith("VIEW:STAFF");
        assertThat(SecurityHelper.isStaffOrAdmin()).isTrue();
    }

    @Test
    void isStaffOrAdmin_falseForClient() {
        authWith("VIEW:LOYALTY", "VIEW:COMMUNITY"); // autorités CLIENT typiques, pas VIEW:STAFF
        assertThat(SecurityHelper.isStaffOrAdmin()).isFalse();
    }

    @Test
    void hasAuthority_matchesExactString() {
        authWith("CREATE:ENROLLMENTS");
        assertThat(SecurityHelper.hasAuthority("CREATE:ENROLLMENTS")).isTrue();
        assertThat(SecurityHelper.hasAuthority("CREATE:LOYALTY")).isFalse();
    }

    @Test
    void requireOwnerOrAdmin_adminBypassesNonOwner() {
        UUID admin = UUID.randomUUID();
        UUID otherResource = UUID.randomUUID();
        jwtAuth(admin, "VIEW:USERS"); // admin
        assertThatCode(() -> SecurityHelper.requireOwnerOrAdmin(otherResource)).doesNotThrowAnyException();
    }

    @Test
    void requireOwnerOrAdmin_nonOwnerNonAdmin_throwsForbidden() {
        UUID caller = UUID.randomUUID();
        UUID otherResource = UUID.randomUUID();
        jwtAuth(caller, "VIEW:LOYALTY"); // ni owner ni admin
        assertThatThrownBy(() -> SecurityHelper.requireOwnerOrAdmin(otherResource))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireOwnerOrAdmin_ownerPasses() {
        UUID caller = UUID.randomUUID();
        jwtAuth(caller, "VIEW:LOYALTY");
        assertThatCode(() -> SecurityHelper.requireOwnerOrAdmin(caller)).doesNotThrowAnyException();
    }
}
