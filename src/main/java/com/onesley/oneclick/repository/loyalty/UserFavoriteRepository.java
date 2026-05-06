package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.UserFavorite;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link UserFavorite} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {
}
