package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.DailyTrendPointDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.OfferInfoDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.RecentReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.RestaurantInfoDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.RestaurantKpiDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.StaffMemberDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDetailDto;
import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.TenantRestaurantDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service portail tenant-admin « Mes restaurants » (C4.2).
 *
 * <p>Native SQL (Modulith CLOSED, aucun import cross-module ; calque {@code TenantClientsService}).
 * {@code restaurants.tenant_id} étant direct, les requêtes sont légères. CA =
 * {@code loyalty_transactions.amount} (earn Snap2Earn) ; staff actif = {@code restaurant_staffs.deleted_at IS NULL}
 * ({@code role_code}) ; offres actives = {@code enabled AND expires_at > NOW()}.
 */
@Service
@Transactional(readOnly = true)
public class TenantRestaurantsService {

    @PersistenceContext
    private EntityManager em;

    // ─── Liste enrichie ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<TenantRestaurantDto> list(UUID tenantId) {
        Map<UUID, Acc> acc = new LinkedHashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT id, name, city, cuisine, image, status, google_rating, google_reviews_count, phone, address
              FROM restaurants
             WHERE tenant_id = :t AND deleted_at IS NULL
             ORDER BY name
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = new Acc((UUID) r[0]);
            a.name = (String) r[1]; a.city = (String) r[2]; a.cuisine = (String) r[3]; a.image = (String) r[4];
            a.status = (String) r[5]; a.googleRating = toBd(r[6]); a.googleReviewsCount = (Integer) r[7];
            a.phone = (String) r[8]; a.address = (String) r[9];
            acc.put(a.id, a);
        }
        if (acc.isEmpty()) return List.of();

        // CA + tickets 30j + dernière activité ticket (Snap2Earn).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT la.restaurant_id, COALESCE(SUM(lt.amount), 0), COUNT(*), MAX(lt.created_at)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
              JOIN restaurants rs ON rs.id = la.restaurant_id
             WHERE rs.tenant_id = :t AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - INTERVAL '30 day'
             GROUP BY la.restaurant_id
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.ca30j = toBd(r[1]); a.tickets30j = num(r[2]); a.lastActivityAt = maxInstant(a.lastActivityAt, toInstant(r[3])); }
        }

        // Réservations 30j + dernière activité résa.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT restaurant_id, COUNT(*), MAX(created_at)
              FROM reservations
             WHERE tenant_id = :t AND deleted_at IS NULL AND created_at >= NOW() - INTERVAL '30 day'
             GROUP BY restaurant_id
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.reservations30j = num(r[1]); a.lastActivityAt = maxInstant(a.lastActivityAt, toInstant(r[2])); }
        }

        // Staff actif (soft-delete).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.restaurant_id, COUNT(*)
              FROM restaurant_staffs rs
              JOIN restaurants r ON r.id = rs.restaurant_id
             WHERE r.tenant_id = :t AND rs.deleted_at IS NULL
             GROUP BY rs.restaurant_id
            """).setParameter("t", tenantId).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.staffCount = num(r[1]);
        }

        return acc.values().stream().map(Acc::toDto).toList();
    }

    // ─── Fiche détaillée ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public TenantRestaurantDetailDto detail(UUID tenantId, UUID restaurantId) {
        List<Object[]> base = em.createNativeQuery("""
            SELECT id, name, city, cuisine, image, status, google_rating, google_reviews_count, phone, address,
                   website_url, opening_hours, latitude, longitude, created_at
              FROM restaurants
             WHERE id = :r AND tenant_id = :t AND deleted_at IS NULL
            """).setParameter("r", restaurantId).setParameter("t", tenantId).getResultList();
        if (base.isEmpty()) return null;
        Object[] b = base.get(0);
        RestaurantInfoDto info = new RestaurantInfoDto(
            (UUID) b[0], (String) b[1], (String) b[2], (String) b[3], (String) b[4], (String) b[5],
            toBd(b[6]), (Integer) b[7], (String) b[8], (String) b[9], (String) b[10],
            b[11] == null ? null : b[11].toString(), toBdN(b[12]), toBdN(b[13]), toInstant(b[14]));

        // CA + tickets 30j / 30j précédents / points distribués (Snap2Earn de CE resto).
        Object[] cur = (Object[]) em.createNativeQuery("""
            SELECT COALESCE(SUM(lt.amount), 0), COUNT(*), COALESCE(SUM(lt.points), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id = :r AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - INTERVAL '30 day'
            """).setParameter("r", restaurantId).getSingleResult();
        BigDecimal ca30j = toBd(cur[0]);
        long tickets30j = num(cur[1]);
        long pointsDistribues30j = num(cur[2]);

        BigDecimal ca30jPrev = toBd(em.createNativeQuery("""
            SELECT COALESCE(SUM(lt.amount), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id = :r AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - INTERVAL '60 day' AND lt.created_at < NOW() - INTERVAL '30 day'
            """).setParameter("r", restaurantId).getSingleResult());

        long reservations30j = num(em.createNativeQuery("""
            SELECT COUNT(*) FROM reservations
             WHERE restaurant_id = :r AND deleted_at IS NULL AND created_at >= NOW() - INTERVAL '30 day'
            """).setParameter("r", restaurantId).getSingleResult());

        // Staff actif.
        List<StaffMemberDto> staff = new ArrayList<>();
        for (Object[] s : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.id, rs.user_id, rs.role_code, u.first_name, u.last_name
              FROM restaurant_staffs rs
              JOIN users u ON u.id = rs.user_id
             WHERE rs.restaurant_id = :r AND rs.deleted_at IS NULL
             ORDER BY u.first_name, u.last_name
            """).setParameter("r", restaurantId).getResultList()) {
            staff.add(new StaffMemberDto((UUID) s[0], (UUID) s[1], (String) s[2],
                (String) s[3], (String) s[4], "actif"));
        }

        // Offres actives (max 5).
        List<OfferInfoDto> offers = new ArrayList<>();
        for (Object[] o : (List<Object[]>) em.createNativeQuery("""
            SELECT id, title, description, type, pts, expires_at, image
              FROM offers
             WHERE restaurant_id = :r AND deleted_at IS NULL AND enabled = true
               AND (expires_at IS NULL OR expires_at > NOW())
             ORDER BY created_at DESC
             LIMIT 5
            """).setParameter("r", restaurantId).getResultList()) {
            offers.add(new OfferInfoDto((UUID) o[0], (String) o[1], (String) o[2], (String) o[3],
                (Integer) o[4], toInstant(o[5]), (String) o[6]));
        }

        // Dernières réservations (max 10) + nom client.
        List<RecentReservationDto> recent = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT r.id, r.reservation_at, r.guest_count, r.status,
                   TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
              FROM reservations r
              JOIN users u ON u.id = r.client_id
             WHERE r.restaurant_id = :r2 AND r.deleted_at IS NULL
             ORDER BY r.reservation_at DESC
             LIMIT 10
            """).setParameter("r2", restaurantId).getResultList()) {
            String name = (String) r[4];
            recent.add(new RecentReservationDto((UUID) r[0], (name == null || name.isBlank()) ? "Client" : name,
                toInstant(r[1]), r[2] == null ? null : ((Number) r[2]).intValue(), (String) r[3]));
        }

        // Tendance quotidienne (CA + réservations, 30 derniers jours).
        Map<String, BigDecimal> caByDay = new LinkedHashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT to_char(lt.created_at, 'YYYY-MM-DD'), COALESCE(SUM(lt.amount), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id = :r AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
               AND lt.created_at >= NOW() - INTERVAL '30 day'
             GROUP BY 1
            """).setParameter("r", restaurantId).getResultList()) {
            caByDay.put((String) r[0], toBd(r[1]));
        }
        Map<String, Long> resaByDay = new LinkedHashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT to_char(reservation_at, 'YYYY-MM-DD'), COUNT(*)
              FROM reservations
             WHERE restaurant_id = :r AND deleted_at IS NULL AND created_at >= NOW() - INTERVAL '30 day'
             GROUP BY 1
            """).setParameter("r", restaurantId).getResultList()) {
            resaByDay.put((String) r[0], num(r[1]));
        }
        List<DailyTrendPointDto> trend = mergeTrend(caByDay, resaByDay);

        RestaurantKpiDto kpi = new RestaurantKpiDto(ca30j, ca30jPrev, deltaCa(ca30j, ca30jPrev),
            tickets30j, reservations30j, staff.size(), pointsDistribues30j);
        return new TenantRestaurantDetailDto(info, kpi, trend, staff, offers, recent);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Variation de CA en % entre la période courante et la précédente — fonction pure (testée).
     * Renvoie null si aucune base (période précédente nulle/zéro).
     */
    public static Integer deltaCa(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() <= 0) return null;
        return current.subtract(previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(previous, 0, RoundingMode.HALF_UP)
            .intValue();
    }

    /** Fusionne CA/réservations par jour en une tendance triée chronologiquement (label J/M). */
    static List<DailyTrendPointDto> mergeTrend(Map<String, BigDecimal> caByDay, Map<String, Long> resaByDay) {
        return java.util.stream.Stream.concat(caByDay.keySet().stream(), resaByDay.keySet().stream())
            .distinct().sorted()
            .map(d -> new DailyTrendPointDto(d, dayLabel(d),
                caByDay.getOrDefault(d, BigDecimal.ZERO), resaByDay.getOrDefault(d, 0L)))
            .toList();
    }

    /** "2026-06-06" → "6/6". */
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

    /** Comme toBd mais conserve null (coordonnées GPS optionnelles). */
    private static BigDecimal toBdN(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static final class Acc {
        final UUID id;
        String name, city, cuisine, image, status, phone, address;
        BigDecimal googleRating; Integer googleReviewsCount;
        BigDecimal ca30j = BigDecimal.ZERO;
        long tickets30j, reservations30j, staffCount;
        Instant lastActivityAt;

        Acc(UUID id) { this.id = id; }

        TenantRestaurantDto toDto() {
            return new TenantRestaurantDto(id, name, city, cuisine, image, status, googleRating,
                googleReviewsCount, phone, address, ca30j, tickets30j, reservations30j, staffCount, lastActivityAt);
        }
    }
}
