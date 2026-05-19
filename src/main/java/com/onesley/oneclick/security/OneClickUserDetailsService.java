package com.onesley.oneclick.security;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.identity.api.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Bug 34 — Service Spring Security qui charge un {@link UserDetails} depuis le
 * référentiel OneClick (table {@code users} + {@code roles} + {@code permissions}).
 *
 * <p>Pattern senior : 1 seul SELECT par auth (cf
 * {@code UserRepository.findByIdWithRoleAndPermissions} qui fait un JOIN FETCH
 * multi-niveau role → permissions → menu/action), puis mis en cache Redis
 * sous {@link CacheConfig#CACHE_USER_DETAILS} pendant 1 h.
 *
 * <p>Convention {@code username} = UUID string (matche {@code jwt.sub}) — décidée
 * pour éviter une table de correspondance email → uuid et garantir que la
 * granularité du cache soit alignée sur l'identifiant immuable.
 *
 * <p>Eviction : {@link #evictUser(UUID)} appelé par {@code UserService} sur les
 * mutations qui changent l'autorisation (changement de rôle, soft-delete,
 * password reset). Pas d'eviction sur les changements de profil non auth-critique
 * (nom, avatar) pour préserver le hit rate.
 */
@Service
public class OneClickUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public OneClickUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Charge un user par son UUID (passé en string pour matcher l'API Spring
     * Security {@code loadUserByUsername(String)}).
     *
     * <p>{@code @Transactional(readOnly = true)} ouvre une session Hibernate
     * pendant l'extraction des authorities — sécurise les LAZY proxies de
     * {@code role.permissions} même si le JOIN FETCH évite normalement le N+1.
     *
     * <p>{@code @Cacheable} sur la string brute ({@code #username}) : la clé
     * Redis reste lisible ({@code userDetails::e4e2...-...}) et matche
     * directement l'eviction {@code evictUser(uuid)} qui caste en string.
     *
     * @throws UsernameNotFoundException si UUID invalide ou user inconnu/soft-deleted
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_USER_DETAILS, key = "#username", unless = "#result == null")
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("username must not be blank");
        }
        UUID userId;
        try {
            userId = UUID.fromString(username);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("username must be a UUID: " + username, e);
        }
        return userRepository.findByIdWithRoleAndPermissions(userId)
            .map(OneClickUserDetails::from)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }

    /**
     * Eviction ciblée du cache {@code userDetails} pour un user donné. À appeler
     * sur toute mutation qui modifie son rôle, son statut (enabled, account_*),
     * son password ou son soft-delete.
     *
     * <p>Pas de cascade nécessaire ici : 1 user = 1 entrée cache, l'eviction
     * d'un user n'invalide pas les autres.
     */
    @CacheEvict(value = CacheConfig.CACHE_USER_DETAILS, key = "#userId.toString()")
    public void evictUser(UUID userId) {
        // No-op — l'annotation fait le travail. La méthode existe pour servir
        // de hook explicite côté UserService (cherche grep evictUser).
    }
}
