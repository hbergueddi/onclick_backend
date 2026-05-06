package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.EmailBounce;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link EmailBounce} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface EmailBounceRepository extends JpaRepository<EmailBounce, UUID> {
}
