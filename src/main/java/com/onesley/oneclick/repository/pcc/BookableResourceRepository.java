package com.onesley.oneclick.repository.pcc;

import com.onesley.oneclick.entity.pcc.BookableResource;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link BookableResource} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface BookableResourceRepository extends JpaRepository<BookableResource, UUID>, JpaSpecificationExecutor<BookableResource> {
}
