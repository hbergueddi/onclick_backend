package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.ScannedTicket;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ScannedTicket} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ScannedTicketRepository extends JpaRepository<ScannedTicket, UUID> {
}
