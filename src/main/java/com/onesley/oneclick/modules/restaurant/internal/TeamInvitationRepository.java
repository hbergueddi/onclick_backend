package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository {@link TeamInvitation} — invitations d'équipe par restaurant. */
@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {

    List<TeamInvitation> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);
}
