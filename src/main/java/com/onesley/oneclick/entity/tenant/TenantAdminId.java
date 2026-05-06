package com.onesley.oneclick.entity.tenant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant composite de {@link TenantAdmin} — utilisé via {@code @IdClass}.
 *
 * <p>Convention JPA : une {@code @IdClass} doit :
 * <ul>
 *   <li>Implémenter {@link Serializable}</li>
 *   <li>Avoir un constructor public sans args</li>
 *   <li>Définir des champs dont les noms et types correspondent EXACTEMENT à ceux
 *       déclarés {@code @Id} dans l'entité</li>
 *   <li>Implémenter {@link #equals} et {@link #hashCode} (utilisés par le
 *       persistence context pour identifier les entités en cache de premier niveau)</li>
 * </ul>
 *
 * <p>Pas un {@code record} : JPA exige un POJO avec setters/no-arg constructor.
 */
public class TenantAdminId implements Serializable {

    private UUID tenantId;
    private UUID userId;

    public TenantAdminId() {
        // JPA
    }

    public TenantAdminId(UUID tenantId, UUID userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantAdminId other)) return false;
        return Objects.equals(tenantId, other.tenantId)
            && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId);
    }
}
