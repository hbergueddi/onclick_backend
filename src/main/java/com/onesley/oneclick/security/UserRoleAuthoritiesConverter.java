package com.onesley.oneclick.security;

import com.onesley.oneclick.core.identity.api.AuthoritiesProvider;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Convertit un {@link Jwt} en {@link AbstractAuthenticationToken} dont les
 * autorités sont chargées depuis le rôle unique du user + ses permissions.
 *
 * <p>Architecture enterprise (§2.1) : 1 user = 1 rôle. On query
 * {@code users WHERE id = sub} et on prend {@code role.code} comme autorité,
 * puis on JOIN {@code permissions × menus × actions} pour les authorities
 * fines au format {@code VERB:RESOURCE} (RBAC v2 — Bug 32).
 *
 * <p>Authorities exposées (cumul, pas exclusif) :
 * <ul>
 *   <li>{@code ROLE_<code>} — backward-compat {@code hasRole(...)}</li>
 *   <li>{@code <code>} — backward-compat {@code hasAuthority(<role>)}</li>
 *   <li>{@code VIEW:RESTAURANTS}, {@code CREATE:RESERVATIONS}, … —
 *       RBAC v2 senior {@code hasAuthority('VERB:RESOURCE')}</li>
 * </ul>
 *
 * <p>Pendant la transition (PR Bug 32 → Bug 32+N), les controllers utilisent
 * le double-binding {@code hasAnyRole(...) or hasAuthority(...)} — aucune
 * régression possible si les permissions seedées sont incomplètes.
 *
 * <p>Coût : 2 SELECT par requête authentifiée (user+role, puis permissions).
 * Cache Redis à introduire si besoin perf — voir cacheable annotations sur
 * {@code AuthoritiesProviderImpl}.
 */
@Component
public class UserRoleAuthoritiesConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final AuthoritiesProvider authoritiesProvider;

    public UserRoleAuthoritiesConverter(
        UserRepository userRepository,
        AuthoritiesProvider authoritiesProvider
    ) {
        this.userRepository = userRepository;
        this.authoritiesProvider = authoritiesProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (userId != null) {
            // @Transactional ouvre une session Hibernate qui couvre l'accès lazy à user.role
            // → évite LazyInitializationException sur Role proxy quand le converter est
            //   appelé depuis le SecurityFilterChain (hors @Transactional service).
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && user.get().getRole() != null) {
                String code = user.get().getRole().getCode();
                UUID roleId = user.get().getRole().getId();
                // Backward-compat : rôle exposé en double pour hasRole + hasAuthority(role)
                authorities.add(new SimpleGrantedAuthority("ROLE_" + code));
                authorities.add(new SimpleGrantedAuthority(code));
                // Bug 32 — RBAC v2 : permissions du rôle au format VERB:RESOURCE
                for (String perm : authoritiesProvider.findAuthoritiesByRoleId(roleId)) {
                    authorities.add(new SimpleGrantedAuthority(perm));
                }
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static UUID parseUserId(String sub) {
        if (sub == null || sub.isBlank()) return null;
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
