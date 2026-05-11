package com.onesley.oneclick.security;

import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.core.identity.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Convertit un {@link Jwt} en {@link AbstractAuthenticationToken} dont les
 * autorités sont chargées depuis le rôle unique du user (RBAC simplifié).
 *
 * <p>Architecture enterprise (§2.1) : 1 user = 1 rôle. On query
 * {@code users WHERE id = sub} et on prend {@code role.code} comme autorité.
 *
 * <p>Le rôle est exposé en double : {@code ROLE_<code>} (pour {@code hasRole(...)})
 * et {@code <code>} (pour {@code hasAuthority(...)}).
 *
 * <p>Avantages :
 * <ul>
 *   <li>Pas de rôles stockés dans le JWT → changement de rôle effectif immédiat</li>
 *   <li>Source de vérité unique : DB</li>
 *   <li>1 SELECT par requête authentifiée — à cacher en Redis si besoin perf</li>
 * </ul>
 */
@Component
public class UserRoleAuthoritiesConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public UserRoleAuthoritiesConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        List<GrantedAuthority> authorities = List.of();
        if (userId != null) {
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && user.get().getRole() != null) {
                String code = user.get().getRole().getCode();
                authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + code),
                    new SimpleGrantedAuthority(code)
                );
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
