package com.onesley.oneclick.service.status;

import com.onesley.oneclick.entity.status.EntityStatus;
import com.onesley.oneclick.repository.status.EntityStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service helper pour résoudre les {@link EntityStatus} canoniques en écriture.
 *
 * <p>Pattern d'usage côté services métier :
 * <pre>
 *   tenant.setStatus(entityStatusService.required("tenant", "actif"));
 * </pre>
 *
 * <p>Levée d'{@link IllegalStateException} si le couple (entity_type, code)
 * n'existe pas en DB — c'est un bug de programmation, pas un cas métier
 * (les codes canoniques sont seedés via Flyway V6).
 */
@Service
@Transactional(readOnly = true)
public class EntityStatusService {

    private final EntityStatusRepository repository;

    public EntityStatusService(EntityStatusRepository repository) {
        this.repository = repository;
    }

    /**
     * Résolution stricte d'un statut canonique. Throw si le code n'existe pas.
     * À utiliser dans les services métier pour assigner un statut à une entité.
     */
    public EntityStatus required(String entityType, String code) {
        return repository.findByEntityTypeAndCode(entityType, code)
            .orElseThrow(() -> new IllegalStateException(
                "Unknown EntityStatus '" + entityType + ":" + code + "' — "
                + "vérifier le seed Flyway V6 ou que l'entity_type est correct."));
    }

    /**
     * Listing pour UI dropdown (statuts actifs uniquement, triés par sort_order).
     */
    public List<EntityStatus> listActive(String entityType) {
        return repository.findByEntityTypeAndIsActiveTrueOrderBySortOrderAsc(entityType);
    }

    /**
     * Listing complet (actifs + désactivés) pour panneau admin.
     */
    public List<EntityStatus> listAll(String entityType) {
        return repository.findByEntityTypeOrderBySortOrderAsc(entityType);
    }
}
