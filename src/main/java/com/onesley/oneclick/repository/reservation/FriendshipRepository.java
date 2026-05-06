package com.onesley.oneclick.repository.reservation;

import com.onesley.oneclick.entity.reservation.Friendship;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link Friendship} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
}
