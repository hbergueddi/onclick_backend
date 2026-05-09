package com.onesley.oneclick.repository.status;

import com.onesley.oneclick.entity.status.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link EntityStatus} — accès CRUD + finders pour la table de
 * référence des statuts métier.
 *
 * <h3>Finders principaux</h3>
 * <ul>
 *   <li>{@link #findByEntityTypeAndCode} : lookup canonique pour la résolution
 *       d'un statut depuis un code. Utilisé par tous les services métier
 *       lors de l'écriture (ex: {@code reservationService.confirm()} → résoudre
 *       le code "confirmee" en EntityStatus).</li>
 *   <li>{@link #findByEntityTypeAndIsActiveTrueOrderBySortOrderAsc} : listing
 *       pour les dropdowns UI, filtré sur les statuts actifs.</li>
 *   <li>{@link #findByEntityType} : tous les statuts d'une entité, actifs ou non
 *       (panneau admin pour gérer la matrice des statuts).</li>
 * </ul>
 */
@Repository
public interface EntityStatusRepository extends JpaRepository<EntityStatus, UUID> {

    /**
     * Lookup canonique d'un statut par son couple (entity_type, code).
     * Utilisé partout en écriture pour résoudre un code métier en EntityStatus.
     */
    Optional<EntityStatus> findByEntityTypeAndCode(String entityType, String code);

    /**
     * Listing actif trié pour UI dropdown (par défaut on n'expose pas les
     * statuts désactivés au choix utilisateur).
     */
    List<EntityStatus> findByEntityTypeAndIsActiveTrueOrderBySortOrderAsc(String entityType);

    /**
     * Listing complet (actifs + inactifs) pour admin panel.
     */
    List<EntityStatus> findByEntityTypeOrderBySortOrderAsc(String entityType);

    /**
     * Vérifie l'existence d'un statut sans le charger — utile pour les
     * validations service-side avant un INSERT.
     */
    boolean existsByEntityTypeAndCode(String entityType, String code);
}
