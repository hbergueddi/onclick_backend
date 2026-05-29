package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.*;
import com.onesley.oneclick.security.SecurityHelper;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service d'agrégations admin Sprint H — vues users, recycling-pool, HI cockpit, wallet.
 *
 * <p>Native SQL pour éviter import cross-module d'entités (Modulith Type.CLOSED).
 */
@Service
@Transactional(readOnly = true)
public class AdminViewsService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<AdminUserDto> findAdminUsers(String search, String roleCode, UUID tenantId, int limit) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted_at IS NULL ");
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(u.first_name) LIKE :s OR LOWER(u.last_name) LIKE :s OR LOWER(u.email) LIKE :s) ");
        }
        if (roleCode != null) where.append(" AND r.code = :role ");
        if (tenantId != null) where.append(" AND u.tenant_id = :tenantId ");

        String sql = """
            SELECT u.id, u.first_name, u.last_name, u.email, u.phone,
                   r.code,
                   u.tenant_id,
                   COALESCE((SELECT SUM(balance) FROM loyalty_accounts la WHERE la.client_id = u.id AND la.deleted_at IS NULL), 0) AS pts,
                   (SELECT COUNT(*) FROM reservations WHERE client_id = u.id AND deleted_at IS NULL) AS resa,
                   (SELECT COUNT(*) FROM loyalty_transactions lt
                      JOIN loyalty_accounts la ON la.id = lt.account_id
                     WHERE la.client_id = u.id AND lt.type = 'earn') AS tickets,
                   u.created_at
              FROM users u
              LEFT JOIN roles r ON r.id = u.role_id
              """ + where + """
              ORDER BY u.created_at DESC
              LIMIT :limit
            """;
        var q = em.createNativeQuery(sql);
        if (search != null && !search.isBlank()) q.setParameter("s", "%" + search.toLowerCase() + "%");
        if (roleCode != null) q.setParameter("role", roleCode);
        if (tenantId != null) q.setParameter("tenantId", tenantId);
        q.setParameter("limit", limit);

        List<Object[]> rows = q.getResultList();
        return rows.stream().map(row -> new AdminUserDto(
            (UUID) row[0],
            (String) row[1], (String) row[2],
            (String) row[3], (String) row[4],
            (String) row[5],
            row[6] != null ? (UUID) row[6] : null,
            row[7] != null ? ((Number) row[7]).intValue() : 0,
            row[8] != null ? ((Number) row[8]).longValue() : 0L,
            row[9] != null ? ((Number) row[9]).longValue() : 0L,
            row[10] != null ? toInstant(row[10]) : null
        )).toList();
    }

    public AdminWalletSummaryDto walletSummary() {
        // Convention: amount > 0 = credit (commission, gift), amount < 0 = debit
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0),
              COALESCE(SUM(-amount) FILTER (WHERE amount < 0), 0),
              COALESCE(SUM(amount), 0),
              COUNT(*),
              COALESCE(SUM(amount) FILTER (WHERE amount > 0 AND created_at > NOW() - INTERVAL '30 days'), 0),
              COALESCE(SUM(-amount) FILTER (WHERE amount < 0 AND created_at > NOW() - INTERVAL '30 days'), 0)
              FROM wallet_transactions
              WHERE deleted_at IS NULL
            """).getSingleResult();
        return new AdminWalletSummaryDto(
            toBd(row[0]), toBd(row[1]), toBd(row[2]),
            row[3] != null ? ((Number) row[3]).longValue() : 0L,
            toBd(row[4]), toBd(row[5])
        );
    }

    /**
     * RBAC : admin → tout ; RESTAURATEUR/STAFF → uniquement leur restaurant
     * (restaurantId obligatoire + check staff actif). Sans cette logique, le
     * frontend Facturation (OneClickHIPro) 403 systématique.
     */
    private void requireAdminOrStaffOf(UUID restaurantId) {
        if (SecurityHelper.isAdmin()) return;
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) throw new ForbiddenException("Non authentifié");
        if (restaurantId == null) {
            throw new BadRequestException(
                "restaurantId obligatoire pour staff non-admin (filtrage scope tenant)"
            );
        }
        Number count = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM restaurant_staffs
             WHERE user_id = :userId AND restaurant_id = :restaurantId
               AND deleted_at IS NULL
            """)
            .setParameter("userId", callerId)
            .setParameter("restaurantId", restaurantId)
            .getSingleResult();
        if (count.longValue() == 0) {
            throw new ForbiddenException("Accès refusé : vous n'êtes pas staff de ce restaurant");
        }
    }

    @SuppressWarnings("unchecked")
    public List<AdminWalletTransactionDto> walletTransactions(UUID userId, UUID restaurantId, int limit) {
        requireAdminOrStaffOf(restaurantId);
        // wallet_transactions has restaurant_id (not user_id directly) — userId param maps to created_by
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        if (userId != null) where.append(" AND created_by = :userId ");
        if (restaurantId != null) where.append(" AND restaurant_id = :restaurantId ");

        var q = em.createNativeQuery("""
            SELECT id, created_by, restaurant_id, amount, type, reason, created_at
              FROM wallet_transactions
              """ + where + """
              ORDER BY created_at DESC
              LIMIT :limit
            """);
        if (userId != null) q.setParameter("userId", userId);
        if (restaurantId != null) q.setParameter("restaurantId", restaurantId);
        q.setParameter("limit", limit);

        List<Object[]> rows = q.getResultList();
        return rows.stream().map(r -> new AdminWalletTransactionDto(
            (UUID) r[0],
            r[1] != null ? (UUID) r[1] : null,
            r[2] != null ? (UUID) r[2] : null,
            toBd(r[3]),
            (String) r[4],
            (String) r[5],
            r[6] != null ? toInstant(r[6]) : null
        )).toList();
    }

    public RecyclingPoolDto recyclingPool() {
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              COALESCE(SUM(amount), 0) AS pool,
              COALESCE(SUM(amount) FILTER (WHERE amount > 0 AND created_at > NOW() - INTERVAL '30 days'), 0),
              COALESCE(SUM(-amount) FILTER (WHERE amount < 0 AND created_at > NOW() - INTERVAL '30 days'), 0),
              COUNT(DISTINCT created_by) AS contributors,
              CASE WHEN COUNT(*) > 0
                   THEN COALESCE(AVG(amount), 0)
                   ELSE 0 END AS avg_ticket
              FROM wallet_transactions
              WHERE deleted_at IS NULL
            """).getSingleResult();
        return new RecyclingPoolDto(
            toBd(row[0]), toBd(row[1]), toBd(row[2]),
            row[3] != null ? ((Number) row[3]).longValue() : 0L,
            toBd(row[4])
        );
    }

    public AdminHICockpitDto adminHICockpit() {
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              (SELECT COUNT(*) FROM restaurants WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND status = 'active'),
              (SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND status = 'expired'),
              (SELECT COALESCE(SUM(total_amount), 0)
                 FROM oneclick_hi_invoices
                WHERE deleted_at IS NULL AND period_month = to_char(NOW(), 'YYYY-MM')),
              (SELECT COALESCE(SUM(total_amount), 0)
                 FROM oneclick_hi_invoices
                WHERE deleted_at IS NULL AND period_month LIKE to_char(NOW(), 'YYYY') || '-%'),
              (SELECT COUNT(*) FROM loyalty_transactions WHERE reason = 'snap2earn' AND created_at > NOW() - INTERVAL '30 days')
            """).getSingleResult();
        return new AdminHICockpitDto(
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(),
            toBd(row[3]),
            toBd(row[4]),
            ((Number) row[5]).longValue()
        );
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    // ─── B1 — Rollup dashboard groupe (anti N+1) ─────────────────────────────

    /** Accumulateur mutable par restaurant pour fusionner les 5 requêtes natives. */
    private static final class Acc {
        BigDecimal ca = BigDecimal.ZERO;
        long points;
        BigDecimal wallet = BigDecimal.ZERO;
        long reservations;
        long honored;
        long tickets;
        long staff;
    }

    /**
     * Rollup agrégé des restaurants d'un groupe — UNE passe par source (5 requêtes
     * groupées par {@code restaurant_id}) au lieu du fan-out N+1 du frontend
     * (6 appels HTTP × N restaurants). Modulith CLOSED → SQL natif cross-domaine.
     *
     * <p>ABAC : admin → tout ; sinon le caller doit être staff actif de TOUS les
     * restaurants demandés (sinon 403). Liste dédupliquée + plafonnée par le contrôleur.
     */
    @SuppressWarnings("unchecked")
    public List<GroupRestaurantRollupDto> groupDashboardRollup(List<UUID> restaurantIds) {
        List<UUID> ids = restaurantIds == null ? List.of()
            : restaurantIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();
        requireAdminOrStaffOfAll(ids);

        // LinkedHashMap : préserve l'ordre demandé + seed à zéro (restos sans data inclus).
        java.util.LinkedHashMap<UUID, Acc> acc = new java.util.LinkedHashMap<>();
        ids.forEach(id -> acc.put(id, new Acc()));

        // 1. Réservations : total + honorées (statut canonique EN).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT restaurant_id, COUNT(*), COUNT(*) FILTER (WHERE status = 'honored')
              FROM reservations
             WHERE restaurant_id IN (:ids) AND deleted_at IS NULL
             GROUP BY restaurant_id
            """).setParameter("ids", ids).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.reservations = ((Number) r[1]).longValue(); a.honored = ((Number) r[2]).longValue(); }
        }

        // 2. Tickets scannés (Snap2Earn) : count + CA (montant). reason 'snap2earn|<ref>|...'.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT la.restaurant_id, COUNT(*), COALESCE(SUM(lt.amount), 0)
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE la.restaurant_id IN (:ids) AND lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
             GROUP BY la.restaurant_id
            """).setParameter("ids", ids).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) { a.tickets = ((Number) r[1]).longValue(); a.ca = toBd(r[2]); }
        }

        // 3. Points : somme des soldes des comptes fidélité.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT restaurant_id, COALESCE(SUM(balance), 0)
              FROM loyalty_accounts
             WHERE restaurant_id IN (:ids)
             GROUP BY restaurant_id
            """).setParameter("ids", ids).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.points = ((Number) r[1]).longValue();
        }

        // 4. Wallet : solde (somme des mouvements).
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT restaurant_id, COALESCE(SUM(amount), 0)
              FROM wallet_transactions
             WHERE restaurant_id IN (:ids)
             GROUP BY restaurant_id
            """).setParameter("ids", ids).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.wallet = toBd(r[1]);
        }

        // 5. Effectif staff actif.
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
            SELECT restaurant_id, COUNT(*)
              FROM restaurant_staffs
             WHERE restaurant_id IN (:ids) AND deleted_at IS NULL
             GROUP BY restaurant_id
            """).setParameter("ids", ids).getResultList()) {
            Acc a = acc.get((UUID) r[0]);
            if (a != null) a.staff = ((Number) r[1]).longValue();
        }

        return acc.entrySet().stream()
            .map(e -> new GroupRestaurantRollupDto(
                e.getKey(), e.getValue().ca, e.getValue().points, e.getValue().wallet,
                e.getValue().reservations, e.getValue().honored, e.getValue().tickets, e.getValue().staff))
            .toList();
    }

    /** ABAC liste : admin → tout ; sinon staff actif de TOUS les restaurants demandés. */
    private void requireAdminOrStaffOfAll(List<UUID> ids) {
        if (SecurityHelper.isAdmin()) return;
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null) throw new ForbiddenException("Non authentifié");
        Number owned = (Number) em.createNativeQuery("""
            SELECT COUNT(DISTINCT restaurant_id) FROM restaurant_staffs
             WHERE user_id = :userId AND restaurant_id IN (:ids) AND deleted_at IS NULL
            """)
            .setParameter("userId", callerId)
            .setParameter("ids", ids)
            .getSingleResult();
        if (owned.longValue() < ids.size()) {
            throw new ForbiddenException("Accès refusé : un ou plusieurs restaurants sont hors de votre périmètre");
        }
    }
}
