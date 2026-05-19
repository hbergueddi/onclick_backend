package com.onesley.oneclick.core.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * API publique du module {@code core.identity} pour exposer les permissions
 * d'un rôle sous forme d'authority strings {@code "VERB:RESOURCE"}.
 *
 * <p>Bug 32 (RBAC v2 senior) — Le {@code UserRoleAuthoritiesConverter} du module
 * {@code security} a besoin de lire les permissions sans accéder directement au
 * repository {@code internal}. Cette interface est l'unique surface publique
 * pour ce besoin (Modulith CLOSED : pas d'import cross-module sur {@code internal}).
 *
 * <p>Implémentation : voir {@code AuthoritiesProviderImpl} dans {@code internal}.
 */
public interface AuthoritiesProvider {

    /**
     * Retourne la liste des authority strings pour un rôle, au format
     * {@code "VERB:RESOURCE"} (ex: {@code "VIEW:RESTAURANTS"}, {@code "CREATE:RESERVATIONS"}).
     *
     * <p>Dérivé d'un JOIN {@code permissions × actions × menus} filtré par
     * {@code role_id}. Les permissions sans menu ou sans action sont exclues
     * (non utilisables pour le pattern VERB:RESOURCE).
     *
     * @param roleId UUID du rôle (cf. {@code User.getRole().getId()})
     * @return       Liste possiblement vide, jamais {@code null}.
     */
    List<String> findAuthoritiesByRoleId(UUID roleId);
}
