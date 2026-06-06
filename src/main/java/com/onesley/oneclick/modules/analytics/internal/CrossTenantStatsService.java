package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantRowDto;
import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantStatsDto;
import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agrégat « CrossTenantDashboard » (C3) — KPIs par tenant sur fenêtre glissante de N jours.
 *
 * <p>Native SQL groupé par {@code tenant_id} (Modulith CLOSED : pas d'import d'entités cross-module),
 * calque du pattern {@code AdminViewsService}. Sources : {@code restaurants} (counts), {@code reservations}
 * ({@code tenant_id} direct + {@code created_at}), CA/tickets/clients via {@code loyalty_transactions}
 * (earn Snap2Earn) → {@code loyalty_accounts.restaurant_id} → {@code restaurants.tenant_id}.
 *
 * <p>{@code offersActives} non inclus (table offers hors périmètre de cet agrégat ; champ comparatif
 * secondaire). Snapshot poussé en temps réel par {@code CrossTenantDashboardPublisher} (STOMP).
 */
@Service
@Transactional(readOnly = true)
public class CrossTenantStatsService {

    @PersistenceContext
    private EntityManager em;

    /** Calcule le snapshot cross-tenant sur une fenêtre de {@code days} jours (borné 1..365). */
    @SuppressWarnings("unchecked")
    public CrossTenantStatsDto compute(int days) {
        final int d = days <= 0 ? 30 : Math.min(days, 365);

        // Tenants actifs (ordre stable par nom).
        Map<UUID, Acc> acc = new LinkedHashMap<>();
        for (Object[] t : (List<Object[]>) em.createNativeQuery("""
            SELECT id, slug, name, status FROM tenants WHERE deleted_at IS NULL ORDER BY name
            """).getResultList()) {
            UUID id = (UUID) t[0];
            acc.put(id, new Acc(id, (String) t[1], (String) t[2], (String) t[3]));
        }
        if (acc.isEmpty()) {
            return new CrossTenantStatsDto(List.of(), summarize(List.of()));
        }

        // 1. Restaurants : total + actifs (status='active'), non supprimés.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT tenant_id, COUNT(*), COUNT(*) FILTER (WHERE status = 'active')
              FROM restaurants WHERE deleted_at IS NULL AND tenant_id IS NOT NULL
             GROUP BY tenant_id
            """).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.restosTotal = num(r[1]); a.restosActifs = num(r[2]); }
        }

        // 2. Réservations créées sur la période.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT tenant_id, COUNT(*)
              FROM reservations
             WHERE deleted_at IS NULL AND created_at >= NOW() - (:days * INTERVAL '1 day')
             GROUP BY tenant_id
            """).setParameter("days", d).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.reservations = num(r[1]);
        }

        // 3. CA + tickets + clients actifs sur la période (Snap2Earn).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.tenant_id, COUNT(*), COALESCE(SUM(lt.amount), 0), COUNT(DISTINCT la.client_id)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - (:days * INTERVAL '1 day')
             GROUP BY rs.tenant_id
            """).setParameter("days", d).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.tickets = num(r[1]); a.caPeriod = toBd(r[2]); a.clients = num(r[3]); }
        }

        // 4. CA période précédente (même durée, décalée) → delta.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.tenant_id, COALESCE(SUM(lt.amount), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - (:days2 * INTERVAL '1 day')
               AND lt.created_at <  NOW() - (:days * INTERVAL '1 day')
             GROUP BY rs.tenant_id
            """).setParameter("days", d).setParameter("days2", d * 2).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.caPrevious = toBd(r[1]);
        }

        // 5. Dernière activité = max(dernier ticket, dernière réservation).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.tenant_id, MAX(lt.created_at)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             GROUP BY rs.tenant_id
            """).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.lastActivity = maxInstant(a.lastActivity, toInstant(r[1]));
        }
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT tenant_id, MAX(created_at) FROM reservations WHERE deleted_at IS NULL GROUP BY tenant_id
            """).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.lastActivity = maxInstant(a.lastActivity, toInstant(r[1]));
        }

        Instant now = Instant.now();
        List<CrossTenantRowDto> rows = acc.values().stream()
            .map(a -> a.toDto(now))
            .sorted(Comparator.comparing(CrossTenantRowDto::caPeriod).reversed())
            .toList();
        return new CrossTenantStatsDto(rows, summarize(rows));
    }

    /**
     * Résumé consolidé (somme des lignes) — fonction pure, testable unitairement.
     * {@code activeTenants} = statut {@code active} ; {@code inactiveTenantsCount} = tenants actifs
     * sans activité depuis &gt; 30j (ou jamais).
     */
    public static CrossTenantSummaryDto summarize(List<CrossTenantRowDto> rows) {
        long totalTenants = rows.size();
        long activeTenants = rows.stream().filter(r -> "active".equals(r.tenantStatus())).count();
        long totalRestaurants = rows.stream().mapToLong(CrossTenantRowDto::restaurantsTotal).sum();
        BigDecimal totalCa = rows.stream().map(CrossTenantRowDto::caPeriod)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalTickets = rows.stream().mapToLong(CrossTenantRowDto::ticketsPeriod).sum();
        long totalReservations = rows.stream().mapToLong(CrossTenantRowDto::reservationsPeriod).sum();
        long totalClients = rows.stream().mapToLong(CrossTenantRowDto::clientsActifs).sum();
        long inactive = rows.stream().filter(r ->
            "active".equals(r.tenantStatus())
            && (r.daysSinceLastActivity() == null || r.daysSinceLastActivity() > 30)).count();
        return new CrossTenantSummaryDto(totalTenants, activeTenants, totalRestaurants,
            totalCa, totalTickets, totalReservations, totalClients, inactive);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static final class Acc {
        final UUID id; final String slug; final String name; final String status;
        long restosTotal; long restosActifs;
        BigDecimal caPeriod = BigDecimal.ZERO; BigDecimal caPrevious = BigDecimal.ZERO;
        long tickets; long reservations; long clients;
        Instant lastActivity;

        Acc(UUID id, String slug, String name, String status) {
            this.id = id; this.slug = slug; this.name = name; this.status = status;
        }

        CrossTenantRowDto toDto(Instant now) {
            int deltaPct;
            if (caPrevious.signum() > 0) {
                deltaPct = caPeriod.subtract(caPrevious)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(caPrevious, 0, RoundingMode.HALF_UP).intValue();
            } else {
                deltaPct = caPeriod.signum() > 0 ? 100 : 0;
            }
            Integer daysSince = lastActivity == null
                ? null
                : (int) Math.max(0, Duration.between(lastActivity, now).toDays());
            return new CrossTenantRowDto(id, slug, name, status, restosTotal, restosActifs,
                caPeriod, caPrevious, deltaPct, tickets, reservations, clients, lastActivity, daysSince);
        }
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
