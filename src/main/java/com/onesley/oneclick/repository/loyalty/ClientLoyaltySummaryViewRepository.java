package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.ClientLoyaltySummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link ClientLoyaltySummaryView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface ClientLoyaltySummaryViewRepository extends Repository<ClientLoyaltySummaryView, UUID> {

    Optional<ClientLoyaltySummaryView> findById(UUID id);

    List<ClientLoyaltySummaryView> findAll();

    long count();
}
