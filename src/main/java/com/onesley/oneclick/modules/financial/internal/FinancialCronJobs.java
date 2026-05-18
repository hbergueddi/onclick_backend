package com.onesley.oneclick.modules.financial.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Crons du module financial — Sprint G.3 (port pg_cron legacy) + Sprint I.3.
 *
 * <p>{@code renewContracts} : prolonge automatiquement les contrats arrivant
 * à échéance dans les 30 jours. Tous les 1er du mois à 2h UTC.
 *
 * <p>{@code generateMonthlyInvoices} (Sprint I.3) : génère les factures mensuelles
 * de commission pour tous les restos actifs (port EF {@code generate-invoices}).
 * Tous les 1er du mois à 3h UTC.
 */
@Component
public class FinancialCronJobs {

    private static final Logger log = LoggerFactory.getLogger(FinancialCronJobs.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 2 1 * *", zone = "UTC")
    @Transactional
    public void renewContracts() {
        log.info("[cron] renewContracts starting (1er du mois)...");
        int updated = em.createNativeQuery("""
                UPDATE contracts
                   SET ends_at = ends_at + INTERVAL '1 year',
                       updated_at = NOW()
                 WHERE deleted_at IS NULL
                   AND status = 'active'
                   AND ends_at IS NOT NULL
                   AND ends_at BETWEEN NOW() AND NOW() + INTERVAL '30 days'
                """).executeUpdate();
        log.info("[cron] renewContracts done: {} contrats renouvelés (+1 an)", updated);
    }

    @Scheduled(cron = "0 0 3 1 * *", zone = "UTC")
    @Transactional
    public void generateMonthlyInvoicesCron() {
        String previousMonth = LocalDate.now().minusMonths(1).format(MONTH_FORMAT);
        log.info("[cron] generateMonthlyInvoices starting for period={}", previousMonth);
        int generated = generateInvoicesForPeriod(previousMonth);
        log.info("[cron] generateMonthlyInvoices done: {} factures générées pour {}", generated, previousMonth);
    }

    /**
     * Génère les factures pour un mois donné (port EF generate-invoices).
     *
     * <p>Algorithme :
     *   1. Pour chaque restaurant actif avec un contrat actif :
     *      - Calculer le CA du mois (SUM amount loyalty_transactions type='earn')
     *      - Appliquer commission_rate du contrat
     *      - Calculer TVA (20% Maroc)
     *      - Créer une oneclick_hi_invoices en statut 'draft'
     *   2. Idempotent : skip si une facture existe déjà pour (restaurant, period)
     *
     * @param periodMonth format "yyyy-MM"
     * @return nombre de factures créées
     */
    @Transactional
    public int generateInvoicesForPeriod(String periodMonth) {
        if (periodMonth == null || !periodMonth.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("periodMonth invalide: " + periodMonth);
        }

        LocalDate periodStart = LocalDate.parse(periodMonth + "-01");
        LocalDate periodEnd = periodStart.plusMonths(1);

        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = em.createNativeQuery("""
            SELECT r.id, r.name, r.tenant_id,
                   COALESCE(SUM(lt.amount), 0) AS total_ca,
                   COALESCE(c.commission_rate, 3) AS commission_rate
              FROM restaurants r
              LEFT JOIN loyalty_accounts la ON la.restaurant_id = r.id AND la.deleted_at IS NULL
              LEFT JOIN loyalty_transactions lt ON lt.account_id = la.id
                 AND lt.type = 'earn'
                 AND lt.created_at >= :periodStart
                 AND lt.created_at <  :periodEnd
              LEFT JOIN contracts c ON c.restaurant_id = r.id AND c.status = 'active' AND c.deleted_at IS NULL
             WHERE r.deleted_at IS NULL
               -- Bug 29 — Spring DB = 'active' (EN), legacy Supabase = 'actif' (FR).
               -- Sans ce fix, le cron mensuel de facturation ne génère AUCUNE
               -- facture (0 restaurants matchent → 0 lignes).
               AND r.status = 'active'
               AND NOT EXISTS (
                 SELECT 1 FROM oneclick_hi_invoices i
                  WHERE i.restaurant_id = r.id
                    AND i.period_month = :period
                    AND i.deleted_at IS NULL
               )
             GROUP BY r.id, r.name, r.tenant_id, c.commission_rate
            HAVING COALESCE(SUM(lt.amount), 0) > 0
            """)
            .setParameter("period", periodMonth)
            .setParameter("periodStart", java.sql.Date.valueOf(periodStart))
            .setParameter("periodEnd", java.sql.Date.valueOf(periodEnd))
            .getResultList();

        int created = 0;
        for (Object[] row : rows) {
            java.util.UUID restaurantId = (java.util.UUID) row[0];
            String name = (String) row[1];
            java.util.UUID tenantId = row[2] != null ? (java.util.UUID) row[2] : null;
            BigDecimal totalCA = new BigDecimal(row[3].toString());
            BigDecimal commissionRate = new BigDecimal(row[4].toString());

            // commission = total_ca * commission_rate / 100
            BigDecimal commission = totalCA.multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal vat = commission.multiply(new BigDecimal("0.20"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

            String invoiceNumber = String.format("OCHI-%s-%s",
                periodMonth.replace("-", ""),
                restaurantId.toString().substring(0, 8));

            em.createNativeQuery("""
                INSERT INTO oneclick_hi_invoices
                  (tenant_id, restaurant_id, invoice_number, period_month,
                   total_amount, vat_amount, status, created_at, updated_at)
                VALUES (:tenantId, :restaurantId, :invoiceNumber, :period,
                        :totalAmount, :vatAmount, 'draft', NOW(), NOW())
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("restaurantId", restaurantId)
                .setParameter("invoiceNumber", invoiceNumber)
                .setParameter("period", periodMonth)
                .setParameter("totalAmount", commission)
                .setParameter("vatAmount", vat)
                .executeUpdate();

            created++;
            log.info("[invoices] created {} for {} (CA={}, commission={})",
                invoiceNumber, name, totalCA, commission);
        }
        return created;
    }
}
