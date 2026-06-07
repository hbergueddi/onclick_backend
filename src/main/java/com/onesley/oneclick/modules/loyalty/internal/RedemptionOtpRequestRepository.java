package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Repository {@link RedemptionOtpRequest} — OTP de conversion (Gap #2). */
@Repository
public interface RedemptionOtpRequestRepository extends JpaRepository<RedemptionOtpRequest, UUID> {

    /** Annule toutes les demandes encore en attente pour un couple (client, restaurant). */
    @Modifying
    @Query("""
        UPDATE RedemptionOtpRequest r SET r.status = 'cancelled'
        WHERE r.clientId = :clientId AND r.restaurantId = :restaurantId AND r.status = 'pending'
        """)
    int cancelPending(@Param("clientId") UUID clientId, @Param("restaurantId") UUID restaurantId);
}
