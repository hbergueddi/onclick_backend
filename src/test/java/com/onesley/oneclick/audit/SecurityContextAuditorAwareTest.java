package com.onesley.oneclick.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link SecurityContextAuditorAware} — couvre toutes les
 * branches de résolution de l'identité d'audit via {@link SecurityContextHolder} :
 * pas d'auth / non authentifié / non-JWT / JWT sub valide / sub vide / sub null /
 * sub non-UUID. La stratégie « jamais d'exception » renvoie {@code empty()} partout
 * sauf pour un {@code sub} UUID valide.
 */
class SecurityContextAuditorAwareTest {

    private final SecurityContextAuditorAware aware = new SecurityContextAuditorAware();

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void setAuth(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Jwt jwtWith(String subjectOrNull) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none");
        if (subjectOrNull != null) b.subject(subjectOrNull);
        else b.claim("scope", "read"); // un claim non-vide est requis par le builder
        return b.build();
    }

    /** Le constructeur 2-args pose authenticated=true (le 1-arg laisse false → court-circuit). */
    private JwtAuthenticationToken authedJwt(String subjectOrNull) {
        return new JwtAuthenticationToken(jwtWith(subjectOrNull), List.of());
    }

    @Test
    void noAuthentication_empty() {
        SecurityContextHolder.clearContext();
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void notAuthenticated_empty() {
        Authentication notAuth = mock(Authentication.class);
        when(notAuth.isAuthenticated()).thenReturn(false);
        setAuth(notAuth);
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void authenticatedButNotJwt_empty() {
        // UsernamePasswordAuthenticationToken (cas tests / login flow) → pas de sub auditable
        setAuth(new UsernamePasswordAuthenticationToken("user", "pw", List.of()));
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void jwtWithValidUuidSubject_present() {
        UUID id = UUID.randomUUID();
        setAuth(authedJwt(id.toString()));
        assertThat(aware.getCurrentAuditor()).contains(id);
    }

    @Test
    void jwtWithBlankSubject_empty() {
        setAuth(authedJwt(""));
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void jwtWithNullSubject_empty() {
        setAuth(authedJwt(null));
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void jwtWithNonUuidSubject_empty() {
        setAuth(authedJwt("pas-un-uuid"));
        assertThat(aware.getCurrentAuditor()).isEmpty();
    }
}
