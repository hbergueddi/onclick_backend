package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.ClientTimelineEventDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDetailDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientKpiDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientProfileDto;
import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantTopRestaurantDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service CRM tenant-admin (C4.1) — agrégat « Mes clients » d'un tenant.
 *
 * <p>Native SQL groupé par {@code client_id} (Modulith CLOSED : aucun import d'entité cross-module),
 * calque {@code CrossTenantStatsService}. Un client du tenant = a au moins 1 ticket Snap2Earn OU 1
 * réservation dans un restaurant du tenant. CA = {@code loyalty_transactions.amount} (earn Snap2Earn) ;
 * points = {@code loyalty_accounts.balance}. Réservations comptées : statut hors
 * {@code cancelled/refused/no_show}. Segmentation = fonction pure {@link #segment} (testable).
 */
@Service
@Transactional(readOnly = true)
public class TenantClientsService {

    /** Statuts de réservation NON comptés comme visite (annulée / refusée / no-show). */
    private static final String NOT_COUNTED = "('cancelled','refused','no_show')";

    @PersistenceContext
    private EntityManager em;

    // ─── Liste CRM ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<TenantClientDto> list(UUID tenantId) {
        Map<UUID, Acc> acc = new LinkedHashMap<>();

        // 1. Tickets Snap2Earn agrégés : CA total/30j, visites (tickets) total/30j, première/dernière.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT la.client_id,
                   COALESCE(SUM(lt.amount), 0),
                   COALESCE(SUM(lt.amount) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '30 day'), 0),
                   COUNT(*),
                   COUNT(*) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '30 day'),
                   MIN(lt.created_at), MAX(lt.created_at)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
             GROUP BY la.client_id
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = ensure(acc, (UUID) r[0]);
            a.caTotal = toBd(r[1]);
            a.ca30j = toBd(r[2]);
            a.visitsTotal += num(r[3]);
            a.visits30j += num(r[4]);
            a.firstVisitAt = minInstant(a.firstVisitAt, toInstant(r[5]));
            a.lastVisitAt = maxInstant(a.lastVisitAt, toInstant(r[6]));
        }

        // 2. Restaurant favori = top par nombre de tickets.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT client_id, restaurant_id, name FROM (
              SELECT la.client_id, rs.id AS restaurant_id, rs.name AS name,
                     ROW_NUMBER() OVER (PARTITION BY la.client_id ORDER BY COUNT(*) DESC, rs.name) AS rn
                FROM loyalty_transactions lt
                JOIN loyalty_accounts la ON la.id = lt.account_id
                JOIN restaurants rs ON rs.id = la.restaurant_id
               WHERE rs.tenant_id = :t AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               GROUP BY la.client_id, rs.id, rs.name
            ) x WHERE rn = 1
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.favoriteRestaurantId = (UUID) r[1]; a.favoriteRestaurantName = (String) r[2]; }
        }

        // 3. Réservations (tenant_id direct) : visites comptées total/30j + première/dernière.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT client_id,
                   COUNT(*) FILTER (WHERE status NOT IN %s),
                   COUNT(*) FILTER (WHERE status NOT IN %s AND created_at >= NOW() - INTERVAL '30 day'),
                   MIN(created_at), MAX(created_at)
              FROM reservations
             WHERE tenant_id = :t AND deleted_at IS NULL
             GROUP BY client_id
            """.formatted(NOT_COUNTED, NOT_COUNTED)).setParameter("t", tenantId).getResultList()) {
            Acc a = ensure(acc, (UUID) r[0]);
            a.visitsTotal += num(r[1]);
            a.visits30j += num(r[2]);
            a.reservations30j += num(r[2]);
            a.firstVisitAt = minInstant(a.firstVisitAt, toInstant(r[3]));
            a.lastVisitAt = maxInstant(a.lastVisitAt, toInstant(r[4]));
        }

        // 4. Solde points = SUM(balance) sur les comptes du client dans les restos du tenant.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT la.client_id, COALESCE(SUM(la.balance), 0)
              FROM loyalty_accounts la
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t
             GROUP BY la.client_id
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.pointsSolde = num(r[1]);
        }

        if (acc.isEmpty()) return List.of();

        // 5. Profils des clients identifiés.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT id, first_name, last_name, email, phone, city, avatar_url
              FROM users WHERE id IN (:ids)
            """).setParameter("ids", acc.keySet()).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) {
                a.firstName = (String) r[1]; a.lastName = (String) r[2]; a.email = (String) r[3];
                a.phone = (String) r[4]; a.city = (String) r[5]; a.avatarUrl = (String) r[6];
            }
        }

        Instant now = Instant.now();
        return acc.values().stream()
            .map(a -> a.toDto(now))
            .sorted(Comparator.comparing(TenantClientDto::caTotal).reversed())
            .toList();
    }

    // ─── Fiche 360° ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public TenantClientDetailDto detail(UUID tenantId, UUID clientId) {
        List<Object[]> prof = em.createNativeQuery("""
            SELECT id, first_name, last_name, email, phone, city, avatar_url, created_at
              FROM users WHERE id = :c
            """).setParameter("c", clientId).getResultList();
        if (prof.isEmpty()) return null;
        Object[] p = prof.get(0);

        // Tickets Snap2Earn du client dans les restos du tenant.
        List<Object[]> tickets = em.createNativeQuery("""
            SELECT lt.id, lt.amount, lt.points, lt.created_at, rs.id, rs.name
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND la.client_id = :c
               AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
             ORDER BY lt.created_at DESC
            """).setParameter("t", tenantId).setParameter("c", clientId).getResultList();

        // Autres mouvements points (gift, spend…) — événements points sans CA.
        List<Object[]> pointsEvents = em.createNativeQuery("""
            SELECT lt.id, lt.points, lt.created_at, lt.reason, rs.name
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND la.client_id = :c
               AND NOT (lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%')
             ORDER BY lt.created_at DESC
            """).setParameter("t", tenantId).setParameter("c", clientId).getResultList();

        // Réservations du client.
        List<Object[]> resas = em.createNativeQuery("""
            SELECT r.id, r.created_at, r.guest_count, r.status, rs.name
              FROM reservations r
              JOIN restaurants rs ON rs.id = r.restaurant_id
             WHERE r.tenant_id = :t AND r.client_id = :c AND r.deleted_at IS NULL
             ORDER BY r.created_at DESC
            """).setParameter("t", tenantId).setParameter("c", clientId).getResultList();

        long pointsSolde = num(em.createNativeQuery("""
            SELECT COALESCE(SUM(la.balance), 0)
              FROM loyalty_accounts la JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND la.client_id = :c
            """).setParameter("t", tenantId).setParameter("c", clientId).getSingleResult());

        Instant now = Instant.now();
        Instant thirty = now.minus(Duration.ofDays(30));

        // KPIs CA / visites.
        BigDecimal caTotal = BigDecimal.ZERO, ca30j = BigDecimal.ZERO;
        Instant firstAt = null, lastAt = null;
        Map<UUID, long[]> restoVisits = new LinkedHashMap<>();   // restoId → [visits]
        Map<UUID, BigDecimal> restoCa = new LinkedHashMap<>();
        Map<UUID, String> restoName = new LinkedHashMap<>();
        for (Object[] t : tickets) {
            BigDecimal amt = toBd(t[1]);
            Instant at = toInstant(t[3]);
            caTotal = caTotal.add(amt);
            if (at != null && !at.isBefore(thirty)) ca30j = ca30j.add(amt);
            firstAt = minInstant(firstAt, at); lastAt = maxInstant(lastAt, at);
            UUID rid = (UUID) t[4];
            restoVisits.computeIfAbsent(rid, k -> new long[]{0})[0]++;
            restoCa.merge(rid, amt, BigDecimal::add);
            restoName.putIfAbsent(rid, (String) t[5]);
        }
        long ticketCount = tickets.size();
        long ticket30 = tickets.stream().filter(t -> {
            Instant at = toInstant(t[3]); return at != null && !at.isBefore(thirty);
        }).count();

        long resaCounted = 0, resa30 = 0;
        for (Object[] r : resas) {
            String status = (String) r[3];
            boolean counted = !("cancelled".equals(status) || "refused".equals(status) || "no_show".equals(status));
            Instant at = toInstant(r[1]);
            if (counted) {
                resaCounted++;
                if (at != null && !at.isBefore(thirty)) resa30++;
            }
            // Resto favori = tickets uniquement (comme le legacy) → les résas ne l'alimentent pas.
            firstAt = minInstant(firstAt, at); lastAt = maxInstant(lastAt, at);
        }

        long visitsTotal = ticketCount + resaCounted;
        long visits30 = ticket30 + resa30;
        String segment = segment(firstAt, ca30j, visits30, lastAt, now);
        BigDecimal avgTicket = ticketCount > 0
            ? caTotal.divide(BigDecimal.valueOf(ticketCount), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Top 3 restaurants (par visites tickets).
        List<TenantTopRestaurantDto> top = restoVisits.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
            .limit(3)
            .map(e -> new TenantTopRestaurantDto(e.getKey(), restoName.get(e.getKey()),
                e.getValue()[0], restoCa.getOrDefault(e.getKey(), BigDecimal.ZERO)))
            .toList();

        // Timeline unifiée (30 derniers événements).
        List<ClientTimelineEventDto> events = new ArrayList<>();
        for (Object[] t : tickets) {
            events.add(new ClientTimelineEventDto("t-" + t[0], "ticket", toInstant(t[3]),
                (String) t[5], "Ticket scanné", toBd(t[1]), (Integer) t[2], null, null));
        }
        for (Object[] e : pointsEvents) {
            events.add(new ClientTimelineEventDto("l-" + e[0], "points", toInstant(e[2]),
                (String) e[4], e[3] == null ? "Points" : (String) e[3], null, (Integer) e[1], null, null));
        }
        for (Object[] r : resas) {
            events.add(new ClientTimelineEventDto("r-" + r[0], "reservation", toInstant(r[1]),
                (String) r[4], "Réservation " + r[3], null, null, (String) r[3],
                r[2] == null ? null : ((Number) r[2]).intValue()));
        }
        events.sort(Comparator.comparing(ClientTimelineEventDto::date,
            Comparator.nullsLast(Comparator.reverseOrder())));
        List<ClientTimelineEventDto> timeline = events.stream().limit(30).toList();

        TenantClientProfileDto profile = new TenantClientProfileDto(
            (UUID) p[0], (String) p[1], (String) p[2], (String) p[3], (String) p[4],
            (String) p[5], (String) p[6], toInstant(p[7]));
        TenantClientKpiDto kpi = new TenantClientKpiDto(
            caTotal, ca30j, visitsTotal, visits30, pointsSolde, segment, firstAt, lastAt, avgTicket);
        return new TenantClientDetailDto(profile, kpi, top, timeline);
    }

    // ─── Segmentation (fonction pure, testée unitairement) ───────────────────────

    /**
     * Segmente un client : nouveau (1ʳᵉ visite &lt; 30j) / vip (CA30j ≥ 1000 ou ≥ 5 visites 30j) /
     * actif (visite récente) / dormant (sinon). {@code firstVisit == null} → nouveau (jamais venu).
     */
    public static String segment(Instant firstVisit, BigDecimal ca30j, long visits30j,
                                 Instant lastVisit, Instant now) {
        Instant thirty = now.minus(Duration.ofDays(30));
        if (firstVisit == null || !firstVisit.isBefore(thirty)) return "nouveau";
        if (ca30j != null && ca30j.compareTo(BigDecimal.valueOf(1000)) >= 0) return "vip";
        if (visits30j >= 5) return "vip";
        if (lastVisit != null && !lastVisit.isBefore(thirty)) return "actif";
        return "dormant";
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static Acc ensure(Map<UUID, Acc> map, UUID id) {
        return map.computeIfAbsent(id, Acc::new);
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static Instant minInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static final class Acc {
        final UUID id;
        String firstName = "", lastName = "", email, phone, city, avatarUrl;
        BigDecimal caTotal = BigDecimal.ZERO, ca30j = BigDecimal.ZERO;
        long visitsTotal, visits30j, reservations30j, pointsSolde;
        Instant firstVisitAt, lastVisitAt;
        UUID favoriteRestaurantId; String favoriteRestaurantName;

        Acc(UUID id) { this.id = id; }

        TenantClientDto toDto(Instant now) {
            return new TenantClientDto(id, firstName, lastName, email, phone, city, avatarUrl,
                caTotal, ca30j, visitsTotal, visits30j, reservations30j, pointsSolde,
                firstVisitAt, lastVisitAt, favoriteRestaurantId, favoriteRestaurantName,
                segment(firstVisitAt, ca30j, visits30j, lastVisitAt, now));
        }
    }
}
