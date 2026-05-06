package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.SupportTicket;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link SupportTicket} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
}
