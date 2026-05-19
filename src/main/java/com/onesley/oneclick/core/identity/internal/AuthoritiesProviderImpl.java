package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.AuthoritiesProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation interne de {@link AuthoritiesProvider}.
 *
 * <p>Bug 32 — Délégation simple au {@link PermissionRepository} qui contient
 * la query JPQL avec JOIN action+menu. Pas de cache pour le V1 — cf. note
 * dans {@code UserRoleAuthoritiesConverter} : 1 SELECT supplémentaire par
 * requête authentifiée, à cacher en Redis si besoin perf.
 */
@Service
class AuthoritiesProviderImpl implements AuthoritiesProvider {

    private final PermissionRepository permissionRepository;

    AuthoritiesProviderImpl(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAuthoritiesByRoleId(UUID roleId) {
        if (roleId == null) return List.of();
        return permissionRepository.findAuthorityStringsByRoleId(roleId);
    }
}
