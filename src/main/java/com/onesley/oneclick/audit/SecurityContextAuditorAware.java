package com.onesley.oneclick.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fournit l'UUID de l'utilisateur authentifié pour les annotations {@code @CreatedBy}
 * et {@code @LastModifiedBy} de {@link BaseEntity}.
 *
 * <p><b>Source de l'identité</b> : {@link SecurityContextHolder} → {@link Authentication}.
 * <ul>
 *   <li>Si l'auth est un {@link JwtAuthenticationToken} (cas API REST authentifiée), on
 *       extrait le claim {@code sub} (subject = UUID Supabase) et on le parse en {@link UUID}.</li>
 *   <li>Si l'auth est anonyme (cron, action système, login flow) ou si le {@code sub}
 *       n'est pas un UUID valide, on retourne {@link Optional#empty()} : la colonne sera
 *       persistée en {@code NULL}.</li>
 * </ul>
 *
 * <p><b>Stratégie volontaire</b> : on ne lève jamais d'exception ici — un échec d'audit
 * ne doit pas bloquer une opération métier légitime (cron, migration, healthcheck).
 */
@Component("auditorAware")
public class SecurityContextAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(subject));
            } catch (IllegalArgumentException e) {
                // sub claim n'est pas un UUID valide -> action non auditable
                return Optional.empty();
            }
        }

        // Anonymous / UsernamePasswordAuthenticationToken (tests) / etc. -> NULL en DB
        return Optional.empty();
    }
}
