package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.*;
import com.onesley.oneclick.security.SecurityHelper;
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

    private static java.time.Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof java.time.Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return java.time.Instant.parse(o.toString());
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
}
