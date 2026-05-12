package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link ContractTemplate} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete : filtrer {@code WHERE deleted_at IS NULL} dans le service via
 * {@code Specification}. Pour la résolution par {@code code} (PDF download),
 * un finder dérivé multi-critères dédié est exposé.
 */
@Repository
public interface ContractTemplateRepository
    extends JpaRepository<ContractTemplate, UUID>, JpaSpecificationExecutor<ContractTemplate> {

    /**
     * Recherche par clé fonctionnelle composite {@code (tenant_id, code,
     * language, version)} — utilisé pour résoudre le template à charger
     * dans {@code ContractDownload} (génération PDF).
     *
     * <p>Filtre {@code is_active = true AND deleted_at IS NULL} pour ne jamais
     * servir un template désactivé ou archivé. Si le tenant n'a pas sa
     * propre version, le service peut retomber sur {@code tenantId = null}
     * (template platform-wide) via un second appel.
     */
    Optional<ContractTemplate> findByTenantIdAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
        UUID tenantId, String code, String language, Integer version);

    /** Variante platform-wide ({@code tenant_id IS NULL}). */
    Optional<ContractTemplate> findByTenantIdIsNullAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
        String code, String language, Integer version);
}
