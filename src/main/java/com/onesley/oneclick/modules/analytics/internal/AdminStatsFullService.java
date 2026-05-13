package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminStatsFullDto;
import com.onesley.oneclick.modules.analytics.api.AdminStatsFullDto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service Sprint H — agrégation admin-stats compatible legacy useAdminStats.
 *
 * <p>Periods supportées : null (all-time), "semaine" (lundi-courant),
 * "mois" (1er-courant), "mois_passe", "annee", "annee_passe".
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsFullService {

    @PersistenceContext
    private EntityManager em;

    public AdminStatsFullDto compute(String period) {
        Instant[] range = periodRange(period);
        Instant[] prevRange = previousPeriodRange(period);

        Instant since = range[0];
        Instant until = range[1];

        // 1. Counts core
        Object[] core = (Object[]) buildCoreQuery(since, until).getSingleResult();
        long totalGroups = ((Number) core[0]).longValue();
        long totalRestaurants = ((Number) core[1]).longValue();
        long activeRestaurants = ((Number) core[2]).longValue();
        long totalProfiles = ((Number) core[3]).longValue();
        long totalClients = ((Number) core[4]).longValue();
        long totalStaff = ((Number) core[5]).longValue();
        long totalOffers = ((Number) core[6]).longValue();
        long activeOffers = ((Number) core[7]).longValue();
        long totalLoyaltyPoints = ((Number) core[8]).longValue();
        BigDecimal avgRating = core[9] != null
            ? new BigDecimal(core[9].toString()).setScale(1, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 2. Reservations
        var resaQuery = em.createNativeQuery("""
            SELECT status, COUNT(*)
              FROM reservations
             WHERE deleted_at IS NULL
              """ + dateFilter("reservations", since, until) + """
             GROUP BY status
            """);
        setPeriodParams(resaQuery, since, until);
        @SuppressWarnings("unchecked")
        List<Object[]> resaRows = resaQuery.getResultList();
        Map<String, Long> resByStatus = new LinkedHashMap<>();
        long totalReservations = 0;
        for (Object[] r : resaRows) {
            String status = (String) r[0];
            long count = ((Number) r[1]).longValue();
            resByStatus.put(status, count);
            totalReservations += count;
        }

        // 3. Tickets (scanned amount + count) — type='earn' = ticket scanné
        var ticketQuery = em.createNativeQuery("""
            SELECT COUNT(*), COALESCE(SUM(amount), 0)
              FROM loyalty_transactions
             WHERE type = 'earn'
              """ + dateFilter("loyalty_transactions", since, until) + """
            """);
        setPeriodParams(ticketQuery, since, until);
        Object[] ticketRow = (Object[]) ticketQuery.getSingleResult();
        long totalScannedTickets = ((Number) ticketRow[0]).longValue();
        BigDecimal totalScannedAmount = ticketRow[1] != null
            ? new BigDecimal(ticketRow[1].toString())
            : BigDecimal.ZERO;

        // 4. Deltas vs previous period
        Long deltaReservations = computeDelta("reservations",
            totalReservations,
            prevRange,
            "deleted_at IS NULL");
        Long deltaScannedAmount = computeDeltaSum("loyalty_transactions", "amount",
            totalScannedAmount,
            prevRange,
            "type = 'earn'");
        Long deltaTickets = computeDelta("loyalty_transactions",
            totalScannedTickets,
            prevRange,
            "type = 'earn'");

        // 5. Cuisine distribution — V2 backend ne stocke pas cuisine sur public.restaurants
        // (whitelabel/legacy uniquement). On retourne une liste vide pour le frontend
        // qui adapte gracieusement (cuisineDistribution.length === 0 → masque la chart).
        List<CuisineDistribDto> cuisineDistribution = java.util.List.of();

        // 6. Daily trend (last 30 days)
        @SuppressWarnings("unchecked")
        List<Object[]> trend = em.createNativeQuery("""
            WITH days AS (
              SELECT generate_series(NOW() - INTERVAL '30 days', NOW(), '1 day')::date AS d
            )
            SELECT d::text,
                   COALESCE((SELECT COUNT(*) FROM reservations r WHERE r.deleted_at IS NULL AND r.created_at::date = days.d), 0) AS resa,
                   COALESCE((SELECT SUM(amount) FROM loyalty_transactions lt WHERE lt.type = 'earn' AND lt.created_at::date = days.d), 0) AS ca,
                   COALESCE((SELECT COUNT(*) FROM loyalty_transactions lt WHERE lt.type = 'earn' AND lt.created_at::date = days.d), 0) AS tickets
              FROM days
             ORDER BY d
            """).getResultList();
        List<DailyTrendDto> dailyTrend = trend.stream()
            .map(r -> new DailyTrendDto(
                (String) r[0],
                ((Number) r[1]).longValue(),
                r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO,
                ((Number) r[3]).longValue()
            )).toList();

        // 7. Top restaurants by CA — JOIN via loyalty_accounts (LT n'a pas de restaurant_id direct)
        @SuppressWarnings("unchecked")
        var tops = em.createNativeQuery("""
            SELECT r.name,
                   COALESCE(SUM(lt.amount), 0),
                   COUNT(lt.*)
              FROM restaurants r
              LEFT JOIN loyalty_accounts la ON la.restaurant_id = r.id AND la.deleted_at IS NULL
              LEFT JOIN loyalty_transactions lt ON lt.account_id = la.id AND lt.type = 'earn'
                  """ + (since != null ? " AND lt.created_at >= :since " : "") + """
                  """ + (until != null ? " AND lt.created_at <  :until " : "") + """
             WHERE r.deleted_at IS NULL
             GROUP BY r.id, r.name
             ORDER BY SUM(lt.amount) DESC NULLS LAST
             LIMIT 8
            """);
        setPeriodParams(tops, since, until);
        @SuppressWarnings("unchecked")
        List<Object[]> topsList = tops.getResultList();
        List<TopRestaurantDto> topRestaurants = topsList.stream()
            .filter(r -> r[1] != null && ((Number) r[1]).doubleValue() > 0)
            .map(r -> new TopRestaurantDto(
                (String) r[0],
                new BigDecimal(r[1].toString()),
                ((Number) r[2]).longValue()
            )).toList();

        // 8. Staff breakdown — table = restaurant_staffs (plural), col = role_code
        @SuppressWarnings("unchecked")
        List<Object[]> staffRows = em.createNativeQuery("""
            SELECT role_code, COUNT(*)
              FROM restaurant_staffs
             WHERE deleted_at IS NULL
             GROUP BY role_code
             ORDER BY COUNT(*) DESC
            """).getResultList();
        Map<String, String> labels = Map.of(
            "owner", "Owners", "manager", "Managers", "directeur", "Directeurs",
            "serveur", "Serveurs", "barman", "Barmans", "chef_de_rang", "Chefs de rang",
            "caissier", "Caissiers", "controleur", "Contrôleurs",
            "responsable_resa", "Resp. résa", "waiter", "Waiters"
        );
        List<StaffBreakdownDto> staffBreakdown = staffRows.stream()
            .map(r -> {
                String role = (String) r[0];
                return new StaffBreakdownDto(
                    role,
                    labels.getOrDefault(role, role),
                    ((Number) r[1]).longValue()
                );
            }).toList();

        return new AdminStatsFullDto(
            totalGroups, totalRestaurants, activeRestaurants,
            totalProfiles, totalClients, totalStaff,
            totalReservations, resByStatus,
            totalLoyaltyPoints, totalOffers, activeOffers,
            totalScannedTickets, totalScannedAmount, avgRating,
            deltaReservations, deltaScannedAmount, deltaTickets,
            cuisineDistribution, dailyTrend, topRestaurants, staffBreakdown
        );
    }

    private jakarta.persistence.Query buildCoreQuery(Instant since, Instant until) {
        var q = em.createNativeQuery("""
            SELECT
              (SELECT COUNT(*) FROM restaurant_groups WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM restaurants WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM restaurants WHERE deleted_at IS NULL AND status = 'actif'),
              (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL),
              (SELECT COUNT(u.*) FROM users u JOIN roles r ON r.id = u.role_id WHERE u.deleted_at IS NULL AND r.code = 'CLIENT'),
              (SELECT COUNT(*) FROM restaurant_staffs WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL AND enabled = true AND (expires_at IS NULL OR expires_at > NOW())),
              (SELECT COALESCE(SUM(balance), 0) FROM loyalty_accounts WHERE deleted_at IS NULL),
              (SELECT AVG(google_rating) FROM restaurants WHERE deleted_at IS NULL AND google_rating > 0)
            """);
        return q;
    }

    private String dateFilter(String table, Instant since, Instant until) {
        StringBuilder b = new StringBuilder();
        if (since != null) b.append(" AND ").append(table).append(".created_at >= :since ");
        if (until != null) b.append(" AND ").append(table).append(".created_at <  :until ");
        return b.toString();
    }

    private void setPeriodParams(jakarta.persistence.Query q, Instant since, Instant until) {
        try {
            if (since != null) q.setParameter("since", since);
        } catch (IllegalArgumentException ignored) {}
        try {
            if (until != null) q.setParameter("until", until);
        } catch (IllegalArgumentException ignored) {}
    }

    private Long computeDelta(String table, long current, Instant[] prevRange, String extraWhere) {
        if (prevRange[0] == null || prevRange[1] == null) return null;
        var q = em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + extraWhere +
            " AND created_at >= :since AND created_at < :until");
        q.setParameter("since", prevRange[0]);
        q.setParameter("until", prevRange[1]);
        long prev = ((Number) q.getSingleResult()).longValue();
        if (prev <= 0) return null;
        return Math.round(((double) (current - prev) / prev) * 100);
    }

    private Long computeDeltaSum(String table, String column, BigDecimal current, Instant[] prevRange, String extraWhere) {
        if (prevRange[0] == null || prevRange[1] == null) return null;
        var q = em.createNativeQuery("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table +
            " WHERE " + extraWhere + " AND created_at >= :since AND created_at < :until");
        q.setParameter("since", prevRange[0]);
        q.setParameter("until", prevRange[1]);
        Object res = q.getSingleResult();
        BigDecimal prev = res != null ? new BigDecimal(res.toString()) : BigDecimal.ZERO;
        if (prev.signum() <= 0) return null;
        return Math.round(current.subtract(prev).doubleValue() / prev.doubleValue() * 100);
    }

    private Instant[] periodRange(String period) {
        if (period == null) return new Instant[]{null, null};
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "semaine" -> {
                LocalDate monday = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7);
                yield new Instant[]{monday.atStartOfDay().toInstant(ZoneOffset.UTC), null};
            }
            case "mois" -> new Instant[]{today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC), null};
            case "mois_passe" -> {
                LocalDate firstThis = today.withDayOfMonth(1);
                LocalDate firstLast = firstThis.minusMonths(1);
                yield new Instant[]{firstLast.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstThis.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            case "annee" -> new Instant[]{LocalDate.of(today.getYear(), 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC), null};
            case "annee_passe" -> {
                LocalDate firstThisYear = LocalDate.of(today.getYear(), 1, 1);
                LocalDate firstLastYear = firstThisYear.minusYears(1);
                yield new Instant[]{firstLastYear.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstThisYear.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            default -> new Instant[]{null, null};
        };
    }

    private Instant[] previousPeriodRange(String period) {
        if (period == null) return new Instant[]{null, null};
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "semaine" -> {
                LocalDate monday = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7);
                LocalDate prevMonday = monday.minusDays(7);
                yield new Instant[]{prevMonday.atStartOfDay().toInstant(ZoneOffset.UTC),
                    monday.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            case "mois" -> {
                LocalDate firstThis = today.withDayOfMonth(1);
                LocalDate firstLast = firstThis.minusMonths(1);
                yield new Instant[]{firstLast.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstThis.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            case "mois_passe" -> {
                LocalDate firstThis = today.withDayOfMonth(1);
                LocalDate firstLast = firstThis.minusMonths(1);
                LocalDate firstTwoMonthsAgo = firstThis.minusMonths(2);
                yield new Instant[]{firstTwoMonthsAgo.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstLast.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            case "annee" -> {
                LocalDate firstThisYear = LocalDate.of(today.getYear(), 1, 1);
                LocalDate firstLastYear = firstThisYear.minusYears(1);
                yield new Instant[]{firstLastYear.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstThisYear.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            case "annee_passe" -> {
                LocalDate firstThisYear = LocalDate.of(today.getYear(), 1, 1);
                LocalDate firstLastYear = firstThisYear.minusYears(1);
                LocalDate first2YearsAgo = firstThisYear.minusYears(2);
                yield new Instant[]{first2YearsAgo.atStartOfDay().toInstant(ZoneOffset.UTC),
                    firstLastYear.atStartOfDay().toInstant(ZoneOffset.UTC)};
            }
            default -> new Instant[]{null, null};
        };
    }
}
