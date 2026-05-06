package com.onesley.oneclick.repository.pcc;

import com.onesley.oneclick.entity.pcc.SeminarRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link SeminarRequest} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface SeminarRequestRepository extends JpaRepository<SeminarRequest, UUID> {
}
