package com.onesley.oneclick.repository.reservation;

import com.onesley.oneclick.entity.reservation.ReservationGuest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ReservationGuest} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ReservationGuestRepository extends JpaRepository<ReservationGuest, UUID> {
}
