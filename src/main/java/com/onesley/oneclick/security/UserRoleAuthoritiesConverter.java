package com.onesley.oneclick.security;

import com.onesley.oneclick.entity.auth.UserRole;
import com.onesley.oneclick.repository.auth.UserRoleRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Convertit un {@link Jwt} Supabase en {@link AbstractAuthenticationToken}
 * dont les autorités sont chargées depuis la table {@code user_roles}.
 *
 * <p>Source des autorités :
 * <ul>
 *   <li>Le claim {@code sub} du JWT = l'UUID de l'utilisateur (Supabase
 *       {@code auth.users.id} = OneClick {@code profiles.id})</li>
 *   <li>On query {@code user_roles WHERE user_id = sub} pour récupérer la
 *       liste des rôles applicatifs</li>
 *   <li>Chaque rôle DB devient une autorité Spring Security {@code ROLE_<role>}
 *       — préfixe standard utilisé par {@code hasRole(...)} dans les
 *       expressions {@code @PreAuthorize}</li>
 * </ul>
 *
 * <p>Avantages de cette approche :
 * <ul>
 *   <li>Les rôles ne sont PAS stockés dans le JWT — un changement de rôle est
 *       reflété immédiatement (pas besoin d'attendre l'expiration du token)</li>
 *   <li>Source de vérité unique : la DB OneClick</li>
 *   <li>Si Supabase met des rôles dans le JWT (cas Supabase RBAC), on les ignore
 *       — on fait confiance à notre propre table {@code user_roles}</li>
 * </ul>
 *
 * <p>Coût : 1 SELECT additionnel par requête authentifiée (sur un index
 * {@code idx_user_roles_user_id}). En Phase 11+ on pourra cacher en Redis.
 */
@Component
public class UserRoleAuthoritiesConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRoleRepository userRoleRepository;

    public UserRoleAuthoritiesConverter(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        List<GrantedAuthority> authorities = userId == null
            ? List.of()
            : userRoleRepository.findAllByUserId(userId).stream()
                .map(UserRole::getRole)
                .flatMap(role -> Stream.of(
                    new SimpleGrantedAuthority("ROLE_" + role.name()),
                    new SimpleGrantedAuthority(role.name()) // bare name for hasAuthority(...)
                ))
                .map(GrantedAuthority.class::cast)
                .toList();
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
