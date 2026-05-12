package com.onesley.oneclick.modules.support.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link TicketMessage} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID>, JpaSpecificationExecutor<TicketMessage> {
    java.util.List<TicketMessage> findAllByTicketId(java.util.UUID ticketId);
    java.util.List<TicketMessage> findAllByAuthorId(java.util.UUID authorId);
}
