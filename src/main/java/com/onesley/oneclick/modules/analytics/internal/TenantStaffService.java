package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffMemberDto;
import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantStaffDtos.TenantStaffSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service portail tenant-admin « Équipe » (C4.5).
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). Staff de tous les restos
 * du tenant ({@code restaurant_staffs.deleted_at IS NULL} = actif), JOIN restaurants + users.
 * Résumé (owners/managers/staff/cross-resto) = fonction pure {@link #summarize} (testée).
 */
@Service
@Transactional(readOnly = true)
public class TenantStaffService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantStaffResultDto list(UUID tenantId) {
        List<TenantStaffMemberDto> rows = new ArrayList<>();
        for (Object[] s : (List<Object[]>) em.createNativeQuery("""
            SELECT rs.id, rs.user_id, rs.role_code, rs.restaurant_id, r.name,
                   u.first_name, u.last_name, u.email, u.phone, u.avatar_url
              FROM restaurant_staffs rs
              JOIN restaurants r ON r.id = rs.restaurant_id
              JOIN users u ON u.id = rs.user_id
             WHERE r.tenant_id = :t AND rs.deleted_at IS NULL
             ORDER BY u.first_name, u.last_name
            """).setParameter("t", tenantId).getResultList()) {
            rows.add(new TenantStaffMemberDto(
                (UUID) s[0], (UUID) s[1],
                (String) s[5], (String) s[6], (String) s[7], (String) s[8], (String) s[9],
                (String) s[2], "actif", null,
                (UUID) s[3], (String) s[4]));
        }
        return new TenantStaffResultDto(rows, summarize(rows));
    }

    /**
     * Résumé de l'équipe — fonction pure, testable. Tier dérivé via {@link #roleTier} ;
     * {@code crossResto} = nombre d'users affectés à 2 restos ou plus.
     */
    public static TenantStaffSummaryDto summarize(List<TenantStaffMemberDto> rows) {
        long owners = 0, managers = 0, staff = 0;
        Map<UUID, Integer> restosByUser = new HashMap<>();
        for (TenantStaffMemberDto m : rows) {
            switch (roleTier(m.role())) {
                case "owner" -> owners++;
                case "manager" -> managers++;
                default -> staff++;
            }
            if (m.userId() != null) restosByUser.merge(m.userId(), 1, Integer::sum);
        }
        long crossResto = restosByUser.values().stream().filter(n -> n > 1).count();
        return new TenantStaffSummaryDto(rows.size(), owners, managers, staff, crossResto);
    }

    /** Tier d'un rôle staff : owner / manager / staff (défaut). Fonction pure. */
    public static String roleTier(String role) {
        if (role == null) return "staff";
        return switch (role) {
            case "owner", "directeur" -> "owner";
            case "manager", "responsable_resa", "controleur" -> "manager";
            default -> "staff";
        };
    }
}
