package com.onesley.oneclick.core.tenant.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un tenant whitelabel.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Tenant.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 *
 * <p>{@code legalName} = raison sociale ({@code company_settings.raison_sociale}), peuplé
 * <b>uniquement</b> par {@code TenantService.findById(id)} (vue détail SUPERADMIN). {@code null}
 * sur les chemins {@code findAll()} (liste) et {@code findBySlug()} (PUBLIC, whitelabel routing)
 * — on n'y fait pas le lookup company_settings pour ne pas exposer de donnée légale publiquement
 * ni alourdir le lookup par slug.</p>
 */
public record TenantDto(
    UUID id,
    String name,
    String slug,
    String status,
    Instant createdAt,
    String legalName
) {
}
