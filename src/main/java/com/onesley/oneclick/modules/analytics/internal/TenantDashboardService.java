package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantClientDtos.TenantClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.AlertDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.DailyPointDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TenantDashboardDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TopClientDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.TopRestaurantDto;
import com.onesley.oneclick.modules.analytics.api.TenantDashboardDtos.UpcomingReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service du cockpit tenant-admin (C4.0) — vue d'ensemble « business » d'un tenant (1:1 cockpit
 * tenant legacy 4Click).
 *
 * <p>Native SQL tenant-scopé (Modulith CLOSED, aucun import d'entité cross-module ; calque
 * {@code AdminStatsFullService} pour la fenêtre 30j + delta période-sur-période + dailyTrend). Les
 * tops restos / tops clients / réservations à venir réutilisent les services existants
 * ({@code TenantRestaurantsService}, {@code TenantClientsService}, {@code TenantReservationsService}).
 *
 * <p>Le calcul de delta % et la dérivation d'alertes sont extraits en helpers statiques <b>purs</b>
 * (testés unitairement, sans DB). Le palier ({@code tier}) des top-clients est dérivé depuis la table
 * canonique {@code tiers} du tenant (source unique, jamais de seuils hardcodés) ; si le tenant n'a
 * aucun palier configuré, {@code tier == null} et le front dérive le libellé depuis {@code points}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TenantDashboardService {

    /** Statuts comptés comme « réservation à confirmer » (cockpit : à venir + en attente). */
    private static final java.util.Set<String> UPCOMING_STATUS_SET =
        java.util.Set.of("pending", "confirmed", "counter_proposed");

    @PersistenceContext
    private EntityManager em;

    private final TenantRestaurantsService tenantRestaurantsService;
    private final TenantClientsService tenantClientsService;
    private final TenantReservationsService tenantReservationsService;

    /** Seuil de palier (lu dans la table canonique {@code tiers}). */
    record TierThreshold(int minPoints, String name) {}

    @SuppressWarnings("unchecked")
    public TenantDashboardDto compute(UUID tenantId) {
        // ─── 1. CA 30j + 30j précédents + points distribués (loyalty_transactions earn Snap2Earn) ──
        Object[] caRow = (Object[]) em.createNativeQuery("""
            SELECT
              COALESCE(SUM(lt.amount) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '30 day'), 0),
              COALESCE(SUM(lt.amount) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '60 day'
                                                AND lt.created_at <  NOW() - INTERVAL '30 day'), 0),
              COALESCE(SUM(lt.points) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '30 day'), 0),
              COALESCE(SUM(lt.points) FILTER (WHERE lt.created_at >= NOW() - INTERVAL '60 day'
                                                AND lt.created_at <  NOW() - INTERVAL '30 day'), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
            """).setParameter("t", tenantId).getSingleResult();
        BigDecimal ca30j = toBd(caRow[0]);
        BigDecimal ca30jPrev = toBd(caRow[1]);
        long points30j = num(caRow[2]);
        long points30jPrev = num(caRow[3]);

        // ─── 2. Réservations 30j + 30j précédents (reservations.tenant_id direct, hors soft-delete) ──
        Object[] resaRow = (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '30 day'),
              COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '60 day'
                                 AND created_at <  NOW() - INTERVAL '30 day')
              FROM reservations
             WHERE tenant_id = :t AND deleted_at IS NULL
            """).setParameter("t", tenantId).getSingleResult();
        long reservations30j = num(resaRow[0]);
        long reservations30jPrev = num(resaRow[1]);

        // ─── 3. Clients actifs distincts 30j + 30j précédents (≥1 ticket OU ≥1 réservation comptée) ──
        Object[] clientsRow = (Object[]) em.createNativeQuery("""
            WITH activity AS (
              SELECT la.client_id AS cid, lt.created_at AS at
                FROM loyalty_transactions lt
                JOIN loyalty_accounts la ON la.id = lt.account_id
                JOIN restaurants rs ON rs.id = la.restaurant_id
               WHERE rs.tenant_id = :t AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
              UNION ALL
              SELECT client_id AS cid, created_at AS at
                FROM reservations
               WHERE tenant_id = :t AND deleted_at IS NULL
                 AND status NOT IN ('cancelled','refused','no_show')
            )
            SELECT
              COUNT(DISTINCT cid) FILTER (WHERE at >= NOW() - INTERVAL '30 day'),
              COUNT(DISTINCT cid) FILTER (WHERE at >= NOW() - INTERVAL '60 day'
                                            AND at <  NOW() - INTERVAL '30 day')
              FROM activity
            """).setParameter("t", tenantId).getSingleResult();
        long clientsActifs30j = num(clientsRow[0]);
        long clientsActifs30jPrev = num(clientsRow[1]);

        // ─── 4. Restaurants total / actifs + offres actives ──
        Object[] restoRow = (Object[]) em.createNativeQuery("""
            SELECT
              (SELECT COUNT(*) FROM restaurants WHERE tenant_id = :t AND deleted_at IS NULL),
              (SELECT COUNT(*) FROM restaurants WHERE tenant_id = :t AND deleted_at IS NULL AND status = 'active'),
              (SELECT COUNT(*) FROM offers o JOIN restaurants r ON r.id = o.restaurant_id
                 WHERE r.tenant_id = :t AND o.deleted_at IS NULL AND o.enabled = true
                   AND (o.expires_at IS NULL OR o.expires_at > NOW()))
            """).setParameter("t", tenantId).getSingleResult();
        long restaurantsTotal = num(restoRow[0]);
        long restaurantsActifs = num(restoRow[1]);
        long offresActives = num(restoRow[2]);

        // ─── 5. Tendance quotidienne (CA + réservations, 30 derniers jours, séries complètes) ──
        List<DailyPointDto> dailyTrend = computeDailyTrend(tenantId);

        // ─── 6. Top 5 restaurants (réutilise TenantRestaurantsService) ──
        List<TopRestaurantDto> topRestaurants = tenantRestaurantsService.list(tenantId).stream()
            .sorted(Comparator.comparing(TenantRestaurantDto::ca30j,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .map(r -> new TopRestaurantDto(r.id(), r.name(), r.city(), r.ca30j(), r.tickets30j()))
            .toList();

        // ─── 7. Top 5 clients (réutilise TenantClientsService) — tier dérivé table tiers ──
        List<TierThreshold> tiers = tierThresholds(tenantId);
        List<TopClientDto> topClients = tenantClientsService.list(tenantId).stream()
            .sorted(Comparator.comparing(TenantClientDto::ca30j,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .map(c -> new TopClientDto(
                c.id(), c.firstName(), c.lastName(), c.ca30j(),
                Math.max(0, c.visits30j() - c.reservations30j()),   // tickets purs (visits = tickets + résas)
                c.pointsSolde(),
                tierNameFor(c.pointsSolde(), tiers)))
            .toList();

        // ─── 8. Réservations à venir (7 jours, statuts à confirmer) ──
        List<UpcomingReservationDto> upcoming = computeUpcoming(tenantId);

        // ─── 9. Deltas (helpers purs) + alertes (helper pur) ──
        Integer deltaCa = deltaPercent(ca30j, ca30jPrev);
        Integer deltaReservations = deltaPercent(reservations30j, reservations30jPrev);
        Integer deltaClients = deltaPercent(clientsActifs30j, clientsActifs30jPrev);
        Integer deltaPoints = deltaPercent(points30j, points30jPrev);

        List<AlertDto> alerts = deriveAlerts(
            restaurantsTotal, restaurantsActifs, upcoming, reservations30j);

        return new TenantDashboardDto(
            ca30j, reservations30j, clientsActifs30j, points30j,
            deltaCa, deltaReservations, deltaClients, deltaPoints,
            restaurantsActifs, restaurantsTotal, offresActives,
            dailyTrend, topRestaurants, topClients, upcoming, alerts);
    }

    // ─── Tendance quotidienne (30 jours pleins, jours sans activité = 0) ──────────────

    @SuppressWarnings("unchecked")
    private List<DailyPointDto> computeDailyTrend(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery("""
            WITH days AS (
              SELECT generate_series((NOW() - INTERVAL '29 day')::date, NOW()::date, '1 day')::date AS d
            )
            SELECT to_char(days.d, 'YYYY-MM-DD'),
                   COALESCE((SELECT SUM(lt.amount)
                               FROM loyalty_transactions lt
                               JOIN loyalty_accounts la ON la.id = lt.account_id
                               JOIN restaurants rs ON rs.id = la.restaurant_id
                              WHERE rs.tenant_id = :t AND lt.type = 'earn'
                                AND lt.reason LIKE 'snap2earn|%' AND lt.created_at::date = days.d), 0) AS ca,
                   COALESCE((SELECT COUNT(*)
                               FROM reservations r
                              WHERE r.tenant_id = :t AND r.deleted_at IS NULL
                                AND r.created_at::date = days.d), 0) AS resa
              FROM days
             ORDER BY days.d
            """).setParameter("t", tenantId).getResultList();
        return rows.stream()
            .map(r -> new DailyPointDto((String) r[0], dayLabel((String) r[0]), toBd(r[1]), num(r[2])))
            .toList();
    }

    // ─── Réservations à venir (7 prochains jours, statuts à confirmer) ────────────────

    /**
     * Réservations à venir = réutilise {@code TenantReservationsService.list(tenantId, 7)} (vue
     * transverse enrichie resto + client), puis filtre côté JVM les 7 prochains jours + statuts
     * {@code pending/confirmed/counter_proposed} (helper pur {@link #toUpcoming}). Évite une 2ᵉ requête
     * dédoublonnée et garde la même source de vérité que la vue « Mes réservations ».
     */
    private List<UpcomingReservationDto> computeUpcoming(UUID tenantId) {
        return toUpcoming(tenantReservationsService.list(tenantId, 7).reservations(), Instant.now());
    }

    /**
     * Filtre/mappe une liste de réservations enrichies vers les « à venir » (≥ maintenant, &lt; +7j,
     * statuts {@code pending/confirmed/counter_proposed}) — fonction pure (testable sans DB). Tri
     * chronologique croissant, max 20. Date/heure formatées en UTC (YYYY-MM-DD / HH:mm).
     */
    static List<UpcomingReservationDto> toUpcoming(
        List<com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationDto> rows,
        Instant now) {
        Instant horizon = now.plus(java.time.Duration.ofDays(7));
        return rows.stream()
            .filter(r -> r.reservationAt() != null
                && !r.reservationAt().isBefore(now)
                && r.reservationAt().isBefore(horizon)
                && UPCOMING_STATUS_SET.contains(r.status()))
            .sorted(Comparator.comparing(
                com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationDto::reservationAt))
            .limit(20)
            .map(r -> {
                Instant at = r.reservationAt();
                LocalDate date = at.atOffset(ZoneOffset.UTC).toLocalDate();
                LocalTime time = at.atOffset(ZoneOffset.UTC).toLocalTime();
                String name = (TRIM(r.clientFirstName()) + " " + TRIM(r.clientLastName())).trim();
                return new UpcomingReservationDto(
                    r.id(), r.restaurantName(),
                    name.isBlank() ? "Client" : name,
                    date.toString(),
                    String.format("%02d:%02d", time.getHour(), time.getMinute()),
                    r.couverts(),
                    r.status());
            })
            .toList();
    }

    private static String TRIM(String s) {
        return s == null ? "" : s.trim();
    }

    // ─── Paliers (table canonique tiers du tenant) ───────────────────────────────────

    /**
     * Seuils de paliers du tenant lus dans la table canonique {@code tiers} (source unique, identique
     * à {@code LoyaltyTierResolver}), triés croissant. Vide si le tenant n'a aucun palier configuré
     * (→ {@code tier == null} dans le DTO, libellé dérivé front depuis {@code points}).
     *
     * <p>Lecture SQL native de la table partagée (pas un import d'entité cross-module) — cohérent avec
     * le reste du module analytics qui lit {@code loyalty_transactions}/{@code reservations} en natif.
     */
    @SuppressWarnings("unchecked")
    List<TierThreshold> tierThresholds(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT name, min_points FROM tiers WHERE tenant_id = :t ORDER BY min_points
            """).setParameter("t", tenantId).getResultList();
        List<TierThreshold> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new TierThreshold(r[1] == null ? 0 : ((Number) r[1]).intValue(), (String) r[0]));
        }
        return out;
    }

    // ─── Helpers purs (testés unitairement) ──────────────────────────────────────────

    /**
     * Nom du palier atteint pour {@code points} d'après les seuils DB — fonction pure. {@code null} si
     * aucun palier configuré (le front dérive alors le libellé depuis {@code points}). Sous le palier
     * plancher, renvoie le palier le plus bas (parité {@code LoyaltyTierResolver} : palier plancher
     * renvoyé même en dessous de son seuil).
     */
    public static String tierNameFor(long points, List<TierThreshold> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) return null;
        TierThreshold best = null;
        for (TierThreshold t : thresholds) {
            if (points >= t.minPoints() && (best == null || t.minPoints() >= best.minPoints())) {
                best = t;
            }
        }
        if (best == null) {
            best = thresholds.stream().min(Comparator.comparingInt(TierThreshold::minPoints)).orElse(null);
        }
        return best == null ? null : best.name();
    }

    /**
     * Variation % entre période courante et précédente (entiers) — fonction pure. {@code null} si la
     * base précédente est ≤ 0 (le front affiche « nouveau »). Arrondi HALF_UP.
     */
    public static Integer deltaPercent(long current, long previous) {
        if (previous <= 0) return null;
        return Math.toIntExact(Math.round(((double) (current - previous) / previous) * 100));
    }

    /**
     * Variation % entre deux montants (BigDecimal) — fonction pure. {@code null} si la base précédente
     * est ≤ 0. Arrondi HALF_UP.
     */
    public static Integer deltaPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() <= 0) return null;
        BigDecimal cur = current == null ? BigDecimal.ZERO : current;
        return cur.subtract(previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(previous, 0, RoundingMode.HALF_UP)
            .intValue();
    }

    /**
     * Dérive les alertes opérationnelles d'un tenant — fonction pure (testable sans DB). Conditions :
     * <ul>
     *   <li>{@code danger}/{@code warning} : restaurants inactifs (total &gt; actifs ; danger si ≥ 3) ;</li>
     *   <li>{@code warning} : réservations à venir en attente de confirmation (statut {@code pending}) ;</li>
     *   <li>{@code info} : aucune réservation sur 30j (parc à animer) ;</li>
     *   <li>{@code info} : tout va bien (aucune autre alerte).</li>
     * </ul>
     */
    public static List<AlertDto> deriveAlerts(long restaurantsTotal, long restaurantsActifs,
                                              List<UpcomingReservationDto> upcoming, long reservations30j) {
        List<AlertDto> alerts = new ArrayList<>();

        long inactifs = restaurantsTotal - restaurantsActifs;
        if (inactifs > 0) {
            String type = inactifs >= 3 ? "danger" : "warning";
            alerts.add(new AlertDto(type, "store-off",
                inactifs + " restaurant(s) inactif(s)",
                "Sur " + restaurantsTotal + " restaurant(s), " + inactifs
                    + " ne sont pas au statut actif."));
        }

        long pending = upcoming == null ? 0
            : upcoming.stream().filter(r -> "pending".equals(r.status())).count();
        if (pending > 0) {
            alerts.add(new AlertDto("warning", "clock",
                pending + " réservation(s) à confirmer",
                "Des réservations à venir attendent une confirmation du restaurant."));
        }

        if (reservations30j == 0) {
            alerts.add(new AlertDto("info", "calendar",
                "Aucune réservation sur 30 jours",
                "Aucune réservation enregistrée sur les 30 derniers jours."));
        }

        if (alerts.isEmpty()) {
            alerts.add(new AlertDto("info", "check",
                "Tout est sous contrôle",
                "Aucune alerte opérationnelle pour ce tenant."));
        }
        return alerts;
    }

    /** "2026-06-06" → "6/6" — fonction pure (label de tendance quotidienne). */
    static String dayLabel(String isoDate) {
        String[] p = isoDate.split("-");
        if (p.length != 3) return isoDate;
        return Integer.parseInt(p[2]) + "/" + Integer.parseInt(p[1]);
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }
}
