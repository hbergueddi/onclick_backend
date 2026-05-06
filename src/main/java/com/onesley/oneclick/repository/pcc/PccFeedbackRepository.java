package com.onesley.oneclick.repository.pcc;

import com.onesley.oneclick.entity.pcc.PccFeedback;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link PccFeedback} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface PccFeedbackRepository extends JpaRepository<PccFeedback, UUID>, JpaSpecificationExecutor<PccFeedback> {
}
