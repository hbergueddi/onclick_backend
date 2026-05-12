package com.onesley.oneclick.core.tenant.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un tenant whitelabel.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Tenant.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public record TenantDto(
    UUID id,
    String name,
    String slug,
    String status,
    Instant createdAt
) {
}
