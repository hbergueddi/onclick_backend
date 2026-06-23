package com.onesley.oneclick.core.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link CompanySettings} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID>, JpaSpecificationExecutor<CompanySettings> {

    /**
     * Lookup par tenant. La propriété {@code CompanySettings.tenantId} est typée {@link UUID}
     * (colonne {@code tenant_id uuid}) — le paramètre dérivé doit donc être un {@link UUID}, sinon
     * Hibernate échoue au binding (String vs uuid). Auparavant déclaré {@code String} (jamais
     * exercé) ; corrigé à l'introduction du 1ᵉʳ consommateur (legalName sur la vue détail tenant).
     */
    java.util.Optional<CompanySettings> findByTenantId(UUID tenantId);
}
