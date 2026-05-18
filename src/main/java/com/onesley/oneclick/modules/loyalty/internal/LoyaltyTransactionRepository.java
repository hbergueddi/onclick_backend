package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link LoyaltyTransaction} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID>, JpaSpecificationExecutor<LoyaltyTransaction> {
    java.util.List<LoyaltyTransaction> findAllByAccountId(java.util.UUID accountId);
    java.util.List<LoyaltyTransaction> findAllByCreatedById(java.util.UUID createdBy);

    /**
     * Toutes les transactions d'un client cross-comptes (anti-N+1).
     * JOIN sur {@link LoyaltyAccount} via {@code account_id}, filtrage par {@code client_id}.
     * Tri {@code created_at DESC}, {@link Pageable} pour limiter.
     */
    @Query("""
        SELECT t FROM LoyaltyTransaction t
        WHERE t.accountId IN (
            SELECT a.id FROM LoyaltyAccount a WHERE a.clientId = :clientId
        )
        ORDER BY t.createdAt DESC
        """)
    java.util.List<LoyaltyTransaction> findAllByClientId(@Param("clientId") UUID clientId, Pageable pageable);

    /**
     * Transactions de type {@code expire} d'un client (toutes comptes confondus).
     * Utilisé pour afficher l'historique des points expirés côté Pocket.
     */
    @Query("""
        SELECT t FROM LoyaltyTransaction t
        WHERE t.accountId IN (
            SELECT a.id FROM LoyaltyAccount a WHERE a.clientId = :clientId
        )
        AND t.type = 'expire'
        ORDER BY t.createdAt DESC
        """)
    java.util.List<LoyaltyTransaction> findExpiredByClientId(@Param("clientId") UUID clientId);

    /**
     * Anti-doublon Snap2Earn — vrai si un ticket avec le même {@code ticket_ref}
     * a déjà été scanné pour ce restaurant (toutes les transactions
     * {@code reason} commencent par {@code "snap2earn|<ticket_ref>|..."}).
     *
     * <p>Pas de table {@code scanned_tickets} dédiée dans le greenfield Spring :
     * on stocke le {@code ticket_ref} directement dans {@code reason} avec un
     * séparateur {@code |} pour permettre {@code LIKE 'snap2earn|<ref>|%'}.
     */
    @Query("""
        SELECT COUNT(t) > 0 FROM LoyaltyTransaction t
        WHERE t.accountId IN (
            SELECT a.id FROM LoyaltyAccount a WHERE a.restaurantId = :restaurantId
        )
        AND t.type = 'earn'
        AND t.reason LIKE :reasonPrefix
        """)
    boolean existsSnap2EarnByRestaurantAndTicketRef(
        @Param("restaurantId") UUID restaurantId,
        @Param("reasonPrefix") String reasonPrefix
    );

    /**
     * Sprint G.2.8 — Toutes les transactions d'un restaurant (anti-N+1 pour
     * ProDesk ClientSummary/StaffSummary). JOIN sur LoyaltyAccount.
     * Tri created_at DESC, Pageable pour limiter.
     */
    @Query("""
        SELECT t FROM LoyaltyTransaction t
        WHERE t.accountId IN (
            SELECT a.id FROM LoyaltyAccount a WHERE a.restaurantId = :restaurantId
        )
        ORDER BY t.createdAt DESC
        """)
    java.util.List<LoyaltyTransaction> findAllByRestaurantId(
        @Param("restaurantId") UUID restaurantId,
        Pageable pageable
    );

    /**
     * Bug 28 — Toutes les transactions d'un restaurant ENRICHIES avec
     * {@code clientId} + {@code restaurantId} (anti-N+1 pour PulsePro
     * Dashboard Client qui agrège par client).
     *
     * <p>Diff vs {@link #findAllByRestaurantId} : projection JPQL via constructor
     * expression qui JOIN {@link LoyaltyAccount} et passe les fields enrichis
     * directement dans {@link LoyaltyTransactionDto} — pas de second roundtrip
     * Hibernate, pas de N+1, pas de modification du chemin standard {@code toDto()}.
     *
     * <p>Tri {@code created_at DESC}, {@link Pageable} pour limiter.
     */
    @Query("""
        SELECT new com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto(
            t.id, t.accountId, t.type, t.points, t.amount, t.reason,
            t.expiresAt, t.createdAt, t.createdById,
            a.clientId, a.restaurantId
        )
        FROM LoyaltyTransaction t
        JOIN LoyaltyAccount a ON a.id = t.accountId
        WHERE a.restaurantId = :restaurantId
        ORDER BY t.createdAt DESC
        """)
    java.util.List<LoyaltyTransactionDto> findAllByRestaurantIdEnriched(
        @Param("restaurantId") UUID restaurantId,
        Pageable pageable
    );
}
