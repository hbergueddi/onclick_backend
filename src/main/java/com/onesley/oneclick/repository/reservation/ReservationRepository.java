package com.onesley.oneclick.repository.reservation;

import com.onesley.oneclick.entity.reservation.Reservation;
import com.onesley.oneclick.entity.reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findAllByClientIdOrderByDateDesc(UUID clientId);

    List<Reservation> findAllByRestaurantIdAndDate(UUID restaurantId, LocalDate date);

    List<Reservation> findAllByStatus(ReservationStatus status);

    long countByRestaurantIdAndStatus(UUID restaurantId, ReservationStatus status);
}
