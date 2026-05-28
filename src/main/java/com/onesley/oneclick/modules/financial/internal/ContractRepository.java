package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Repository {@link Contract} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {
    java.util.List<Contract> findAllByRestaurantId(java.util.UUID restaurantId);
    java.util.Optional<Contract> findByContractNumber(String contractNumber);

    /** Contrats actifs en renouvellement auto dont l'échéance tombe avant {@code threshold} (V57 renew). */
    @Query("SELECT c FROM Contract c WHERE c.deletedAt IS NULL AND c.status = 'active' "
        + "AND c.autoRenew = true AND c.endsAt IS NOT NULL AND c.endsAt <= :threshold")
    java.util.List<Contract> findRenewable(@Param("threshold") LocalDate threshold);
}
