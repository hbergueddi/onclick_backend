package com.onesley.oneclick.modules.support.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}
