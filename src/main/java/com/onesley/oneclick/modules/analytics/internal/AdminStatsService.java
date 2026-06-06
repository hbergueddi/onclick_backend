package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminStatsDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service d'agrégation KPI platform-wide — Sprint G.2.4.
 *
 * <p>Pattern senior : agrégats cross-module via native SQL (pas d'import
 * d'entités cross-module — respecte Modulith Type.CLOSED). 1 transaction
 * en lecture seule, 14 COUNT queries en parallèle (Hibernate batch).
 *
 * <p>Filtre {@code tenantId} optionnel : si fourni, scope les COUNT au tenant
 * sinon platform-wide (SUPERADMIN).
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    @PersistenceContext
    private EntityManager em;

    public AdminStatsDto computeStats(UUID tenantId) {
        String tenantFilter = tenantId != null ? "AND tenant_id = :tenantId" : "";

        long totalUsers          = count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL " + tenantFilter, tenantId);
        long totalClients        = count("SELECT COUNT(u.*) FROM users u JOIN roles r ON r.id = u.role_id " +
                                          "WHERE u.deleted_at IS NULL AND r.code = 'CLIENT' " + tenantFilter.replace("tenant_id", "u.tenant_id"), tenantId);
        long totalRestaurateurs  = count("SELECT COUNT(u.*) FROM users u JOIN roles r ON r.id = u.role_id " +
                                          "WHERE u.deleted_at IS NULL AND r.code = 'RESTAURATEUR' " + tenantFilter.replace("tenant_id", "u.tenant_id"), tenantId);
        // Bug 33 — la table s'appelle `restaurant_staffs` (pluriel), pas
        // `restaurant_staff` (singulier). Cf AdminStatsFullService qui utilise
        // déjà le bon nom. Le typo causait un 500 silencieux sur le KPI
        // /api/analytics/admin-stats utilisé par les anciennes pages admin.
        // C3b — `restaurant_staffs` n'a PAS de colonne `tenant_id` : le filtre
        // per-tenant doit passer par un JOIN sur `restaurants` (qui porte le
        // tenant_id). Sans ça, le chemin per-tenant (TenantDashboard) plantait
        // sur « column "tenant_id" does not exist ».
        String staffSql = tenantId != null
            ? "SELECT COUNT(*) FROM restaurant_staffs rs JOIN restaurants r ON r.id = rs.restaurant_id WHERE r.tenant_id = :tenantId"
            : "SELECT COUNT(*) FROM restaurant_staffs";
        long totalStaff          = count(staffSql, tenantId);
        long totalRestaurants    = count("SELECT COUNT(*) FROM restaurants WHERE deleted_at IS NULL " + tenantFilter, tenantId);
        // Bug 29 — Spring DB stocke status='active' (anglais) ; le legacy
        // Supabase utilisait 'actif' (français). Cf AdminStatsFullService idem.
        long activeRestaurants   = count("SELECT COUNT(*) FROM restaurants WHERE deleted_at IS NULL AND status = 'active' " + tenantFilter, tenantId);
        long totalReservations   = count("SELECT COUNT(*) FROM reservations WHERE deleted_at IS NULL " + tenantFilter, tenantId);
        long pendingReservations = count("SELECT COUNT(*) FROM reservations WHERE deleted_at IS NULL AND status = 'pending' " + tenantFilter, tenantId);
        long confirmedReservations = count("SELECT COUNT(*) FROM reservations WHERE deleted_at IS NULL AND status = 'confirmed' " + tenantFilter, tenantId);
        long honoredReservations = count("SELECT COUNT(*) FROM reservations WHERE deleted_at IS NULL AND status = 'honored' " + tenantFilter, tenantId);
        long totalLoyaltyAccounts = count("SELECT COUNT(*) FROM loyalty_accounts WHERE deleted_at IS NULL", null);
        long totalLoyaltyPoints  = countLong("SELECT COALESCE(SUM(balance), 0) FROM loyalty_accounts WHERE deleted_at IS NULL");
        long totalOffers         = count("SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL", null);
        long activeOffers        = count("SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL AND enabled = true AND (expires_at IS NULL OR expires_at > NOW())", null);
        long totalContracts      = count("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL", null);
        long activeContracts     = count("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND status = 'active'", null);
        long openSupportTickets  = count("SELECT COUNT(*) FROM support_tickets WHERE deleted_at IS NULL AND status IN ('open', 'in_progress')", null);

        return new AdminStatsDto(
            totalUsers, totalClients, totalRestaurateurs, totalStaff,
            totalRestaurants, activeRestaurants,
            totalReservations, pendingReservations, confirmedReservations, honoredReservations,
            totalLoyaltyAccounts, totalLoyaltyPoints,
            totalOffers, activeOffers,
            totalContracts, activeContracts,
            openSupportTickets,
            tenantId != null ? tenantId.toString() : null
        );
    }

    /** Helper count avec filtre tenant_id optionnel. */
    private long count(String sql, UUID tenantId) {
        var query = em.createNativeQuery(sql);
        if (tenantId != null && sql.contains(":tenantId")) {
            query.setParameter("tenantId", tenantId);
        }
        Object result = query.getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /** Helper SUM (longueur 64 bits, peut être très grand). */
    private long countLong(String sql) {
        Object result = em.createNativeQuery(sql).getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }
}
