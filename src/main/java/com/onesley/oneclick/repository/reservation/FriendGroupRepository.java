package com.onesley.oneclick.repository.reservation;

import com.onesley.oneclick.entity.reservation.FriendGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link FriendGroup} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface FriendGroupRepository extends JpaRepository<FriendGroup, UUID>, JpaSpecificationExecutor<FriendGroup> {
}
