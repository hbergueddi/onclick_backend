package com.onesley.oneclick.repository.reservation;

import com.onesley.oneclick.entity.reservation.FriendGroupMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link FriendGroupMember} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface FriendGroupMemberRepository extends JpaRepository<FriendGroupMember, UUID> {
}
