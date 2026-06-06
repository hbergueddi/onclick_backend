package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.RestaurantRefDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service portail tenant-admin « Mes réservations » (C4.3).
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). {@code reservations.tenant_id}
 * étant direct, on JOIN restaurants + users pour enrichir, sur une fenêtre glissante (défaut 60j,
 * réservations passées + futures). Résumé = fonction pure {@link #summarize} (testée unitairement).
 */
@Service
@Transactional(readOnly = true)
public class TenantReservationsService {

    /** Garde-fou volume : la vue admin charge jusqu'à 2000 réservations (largement suffisant par tenant). */
    private static final int MAX_ROWS = 2000;

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantReservationsResultDto list(UUID tenantId, int days) {
        final int d = days <= 0 ? 60 : Math.min(days, 365);

        List<RestaurantRefDto> restaurants = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT id, name FROM restaurants WHERE tenant_id = :t AND deleted_at IS NULL ORDER BY name
            """).setParameter("t", tenantId).getResultList()) {
            restaurants.add(new RestaurantRefDto((UUID) r[0], (String) r[1]));
        }

        List<TenantReservationDto> rows = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT r.id, r.reservation_at, r.guest_count, r.status, r.notes, r.created_at,
                   r.restaurant_id, rs.name, rs.city,
                   r.client_id, u.first_name, u.last_name, u.phone, u.email
              FROM reservations r
              JOIN restaurants rs ON rs.id = r.restaurant_id
              JOIN users u ON u.id = r.client_id
             WHERE r.tenant_id = :t AND r.deleted_at IS NULL
               AND r.reservation_at >= NOW() - (:days * INTERVAL '1 day')
             ORDER BY r.reservation_at DESC
             LIMIT :max
            """).setParameter("t", tenantId).setParameter("days", d).setParameter("max", MAX_ROWS).getResultList()) {
            rows.add(new TenantReservationDto(
                (UUID) r[0], toInstant(r[1]), r[2] == null ? 0 : ((Number) r[2]).intValue(),
                (String) r[3], (String) r[4], toInstant(r[5]),
                (UUID) r[6], (String) r[7], (String) r[8],
                (UUID) r[9], (String) r[10], (String) r[11], (String) r[12], (String) r[13]));
        }

        return new TenantReservationsResultDto(rows, summarize(rows, Instant.now()), restaurants);
    }

    /**
     * Résumé d'une liste de réservations (total, ventilations, à venir / aujourd'hui / en attente) —
     * fonction pure, testable unitairement. {@code upcoming} = jour ≥ aujourd'hui (UTC) ;
     * {@code pending} = statut {@code 'pending'} (« demandée » legacy).
     */
    public static TenantReservationsSummaryDto summarize(List<TenantReservationDto> rows, Instant now) {
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant tomorrowStart = todayStart.plus(Duration.ofDays(1));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byRestaurant = new LinkedHashMap<>();
        long upcoming = 0, today = 0, pending = 0;
        for (TenantReservationDto r : rows) {
            if (r.status() != null) byStatus.merge(r.status(), 1L, Long::sum);
            if (r.restaurantId() != null) byRestaurant.merge(r.restaurantId().toString(), 1L, Long::sum);
            Instant at = r.reservationAt();
            if (at != null && !at.isBefore(todayStart)) upcoming++;
            if (at != null && !at.isBefore(todayStart) && at.isBefore(tomorrowStart)) today++;
            if ("pending".equals(r.status())) pending++;
        }
        return new TenantReservationsSummaryDto(rows.size(), byStatus, byRestaurant, upcoming, today, pending);
    }
}
