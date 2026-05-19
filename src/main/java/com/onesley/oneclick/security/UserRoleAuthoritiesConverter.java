package com.onesley.oneclick.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convertit un {@link Jwt} en {@link AbstractAuthenticationToken} dont les
 * autorités proviennent de {@link OneClickUserDetailsService} (Bug 34).
 *
 * <p>Architecture senior (§2.1) : ce converter est un thin adapter qui délègue
 * l'extraction des authorities au service Spring Security standard. Le service
 * gère lui-même le cache Redis ({@code userDetails}, 1 h TTL) et le JOIN FETCH
 * unique role + permissions + menu + action.
 *
 * <p>Historiquement (avant Bug 34) ce converter contenait toute la logique
 * SELECT user + extraction role + appel {@code AuthoritiesProvider} — ce qui
 * forçait à dupliquer le pattern dans tout point d'entrée Spring Security
 * (form login, basic auth, etc). En passant par {@code UserDetailsService}, on
 * réutilise l'infrastructure standard.
 *
 * <p>Authorities produites (identiques à l'historique, cf {@link OneClickUserDetails}) :
 * <ul>
 *   <li>{@code ROLE_<code>} — backward-compat {@code hasRole(...)}</li>
 *   <li>{@code <code>}     — backward-compat {@code hasAuthority(<role>)}</li>
 *   <li>{@code VERB:RESOURCE} — pattern RBAC v2 senior strict (Bug 32)</li>
 * </ul>
 *
 * <p>Si le {@code sub} du JWT ne correspond à aucun user (token forgé/expiré,
 * user soft-deleted post-issue), on renvoie un token sans autorités — Spring
 * Security le traitera comme un 403 sur tout endpoint protégé.
 */
@Component
public class UserRoleAuthoritiesConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OneClickUserDetailsService userDetailsService;

    public UserRoleAuthoritiesConverter(OneClickUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return new JwtAuthenticationToken(jwt, List.of(), sub);
        }
        try {
            UserDetails details = userDetailsService.loadUserByUsername(sub);
            return new JwtAuthenticationToken(jwt, details.getAuthorities(), sub);
        } catch (UsernameNotFoundException notFound) {
            // Token techniquement valide (signature OK) mais user inexistant ou
            // soft-deleted → on retourne un token sans autorités.
            return new JwtAuthenticationToken(jwt, List.of(), sub);
        }
    }
}
