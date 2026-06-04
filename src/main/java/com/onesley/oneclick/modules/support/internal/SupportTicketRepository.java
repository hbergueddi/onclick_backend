package com.onesley.oneclick.modules.support.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository {@link SupportTicket} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {
    java.util.List<SupportTicket> findAllByOpenedBy(java.util.UUID openedBy);
    java.util.List<SupportTicket> findAllByAssignedTo(java.util.UUID assignedTo);

    /**
     * Existence d'un ticket d'une catégorie donnée ouvert par {@code openedBy} depuis
     * {@code since} — sert à la dédup (ex: 1 ticket {@code friends_cap} / 24 h / user).
     * Aligné sur l'index partiel {@code idx_support_tickets_opener_cap} (V64).
     */
    @Query("""
        SELECT (COUNT(t) > 0) FROM SupportTicket t
        WHERE t.openedById = :openedBy
          AND t.category = :category
          AND t.createdAt > :since
        """)
    boolean existsRecentByOpenerAndCategory(
        @Param("openedBy") UUID openedBy,
        @Param("category") String category,
        @Param("since") Instant since);
}
