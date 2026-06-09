package com.onesley.oneclick.core.tenant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de lecture (read-only) de l'annuaire des tenants — exposé hors module ({@code core.tenant}, OPEN).
 *
 * <p>Permet aux autres modules de résoudre un tenant par son slug <b>sans</b> accéder au repository
 * interne ({@code TenantRepository}). Utilisé notamment pour résoudre le <b>tenant public « oneclick »</b>
 * (baseline de visibilité des catalogues client) lors du calcul des tenants visibles d'un caller
 * ({@code MembershipDirectoryApi.visibleTenantIds}).
 */
public interface TenantDirectoryApi {

    /**
     * UUID du tenant portant ce {@code slug} (non soft-deleted), ou vide si introuvable / slug vide.
     */
    Optional<UUID> findIdBySlug(String slug);
}
