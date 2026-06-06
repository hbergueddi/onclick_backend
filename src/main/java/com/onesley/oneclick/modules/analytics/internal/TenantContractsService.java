package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantContractDtos.TenantContractDto;
import com.onesley.oneclick.modules.analytics.api.TenantContractDtos.TenantContractsResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantContractDtos.TenantContractsSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service portail tenant-admin « Contrats » (C4.6), LECTURE SEULE.
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). Contrats des restos du
 * tenant (table {@code contracts} → {@code restaurants} → tenant). Résumé = fonction pure
 * {@link #summarize} (testée). Statut canonique actif = {@code 'active'}.
 */
@Service
@Transactional(readOnly = true)
public class TenantContractsService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantContractsResultDto list(UUID tenantId) {
        List<TenantContractDto> rows = new ArrayList<>();
        for (Object[] c : (List<Object[]>) em.createNativeQuery("""
            SELECT c.id, c.restaurant_id, r.name, r.city, c.status, c.starts_at, c.ends_at,
                   c.commission_rate, c.wallet_admin_rate, c.oneclick_commission_rate,
                   c.auto_renew, c.signed_at, c.contract_number, c.created_at
              FROM contracts c
              JOIN restaurants r ON r.id = c.restaurant_id
             WHERE r.tenant_id = :t AND c.deleted_at IS NULL
             ORDER BY c.created_at DESC
            """).setParameter("t", tenantId).getResultList()) {
            rows.add(new TenantContractDto(
                (UUID) c[0], (UUID) c[1], (String) c[2], (String) c[3], (String) c[4],
                toLocalDate(c[5]), toLocalDate(c[6]),
                toBd(c[7]), toBd(c[8]), toBd(c[9]),
                bool(c[10]), toInstant(c[11]), (String) c[12], toInstant(c[13])));
        }
        return new TenantContractsResultDto(rows, summarize(rows, LocalDate.now()));
    }

    /**
     * Résumé des contrats — fonction pure, testable. {@code active} = statut {@code 'active'} ;
     * {@code expiringSoon} = actif + fin dans (today, today+30j] ; {@code byStatus} = ventilation brute.
     */
    public static TenantContractsSummaryDto summarize(List<TenantContractDto> rows, LocalDate today) {
        LocalDate horizon = today.plusDays(30);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long active = 0, expiringSoon = 0;
        for (TenantContractDto c : rows) {
            if (c.status() != null) byStatus.merge(c.status(), 1L, Long::sum);
            boolean isActive = "active".equals(c.status());
            if (isActive) active++;
            if (isActive && c.contractEnd() != null
                && c.contractEnd().isAfter(today) && !c.contractEnd().isAfter(horizon)) {
                expiringSoon++;
            }
        }
        return new TenantContractsSummaryDto(rows.size(), active, expiringSoon, byStatus);
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        return LocalDate.parse(o.toString());
    }
}
