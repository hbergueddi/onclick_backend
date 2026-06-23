package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.shared.events.ContractExpiringSoonEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Crons du module financial — Sprint G.3 (port pg_cron legacy) + Sprint I.3.
 *
 * <p>{@code renewContracts} : prolonge automatiquement les contrats arrivant
 * à échéance dans les 30 jours <b>et dont {@code auto_renew = true}</b>. Tous les
 * 1er du mois à 2h UTC.
 *
 * <p>{@code alertExpiringContracts} (Feature B, parité legacy {@code notify-expiring-contracts}) :
 * prévient les admins (SUPERADMIN) AVANT l'expiration (J-30/J-15/J-7) des contrats
 * <b>{@code auto_renew = false}</b> (ceux qui vont réellement expirer) — publie un
 * {@link ContractExpiringSoonEvent} par contrat dû ; le listener notification crée la notif in-app
 * (type {@code system}) + push FCM, par admin.
 *
 * <p>{@code generateMonthlyInvoices} (Sprint I.3) : génère les factures mensuelles
 * de commission pour tous les restos actifs (port EF {@code generate-invoices}).
 * Tous les 1er du mois à 3h UTC.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FinancialCronJobs {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Jalons d'alerte avant expiration des contrats : J-30 / J-15 / J-7. */
    private static final int[] MILESTONE_DAYS = {30, 15, 7};

    @PersistenceContext
    private EntityManager em;

    private final ApplicationEventPublisher events;
    private final UserDirectoryApi userDirectory;

    @Scheduled(cron = "0 0 2 1 * *", zone = "UTC")
    @Transactional
    public void renewContracts() {
        log.info("[cron] renewContracts starting (1er du mois)...");
        // Feature B : ne renouvelle QUE les contrats explicitement en auto-renouvellement
        // (auto_renew = true). Les auto_renew = false vont réellement expirer → ils sont gérés
        // par alertExpiringContracts (alerte admin J-30/J-15/J-7), pas renouvelés en silence.
        int updated = em.createNativeQuery("""
                UPDATE contracts
                   SET ends_at = ends_at + INTERVAL '1 year',
                       updated_at = NOW()
                 WHERE deleted_at IS NULL
                   AND status = 'active'
                   AND auto_renew = true
                   AND ends_at IS NOT NULL
                   AND ends_at BETWEEN NOW() AND NOW() + INTERVAL '30 days'
                """).executeUpdate();
        log.info("[cron] renewContracts done: {} contrats renouvelés (+1 an)", updated);
    }

    /**
     * Alerte « contrat partenaire bientôt expiré » (Feature B — parité legacy
     * {@code notify-expiring-contracts}).
     *
     * <p>Pour chaque jalon (J-30/J-15/J-7), sélectionne les contrats {@code active},
     * {@code auto_renew = false}, dont {@code ends_at::date} tombe exactement à ce jalon, et
     * <b>pas encore alertés</b> pour ce jalon (anti-doublon via {@code notifications.metadata}).
     * Résout les admins une fois (SUPERADMIN), puis publie un {@link ContractExpiringSoonEvent}
     * par contrat (portant les destinataires) ; le listener notification crée la notif in-app
     * (type {@code system}) + push FCM, par admin. Tous les matins à 9h heure Maroc.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void alertExpiringContracts() {
        log.info("[cron] alertExpiringContracts starting (J-30/J-15/J-7, auto_renew=false)...");
        List<UUID> admins = userDirectory.adminUserIds();
        if (admins.isEmpty()) {
            log.warn("[cron] alertExpiringContracts: aucun admin (SUPERADMIN) résolu — skip");
            return;
        }
        int published = 0;
        for (int days : MILESTONE_DAYS) {
            String milestone = "j" + days;
            @SuppressWarnings("unchecked")
            List<Object[]> due = em.createNativeQuery("""
                    SELECT c.id, c.restaurant_id, c.contract_number, r.name,
                           to_char((CURRENT_DATE + CAST(:days AS int)), 'DD/MM/YYYY') AS ends_label
                    FROM contracts c
                    LEFT JOIN restaurants r ON r.id = c.restaurant_id AND r.deleted_at IS NULL
                    WHERE c.deleted_at IS NULL
                      AND c.status = 'active'
                      AND c.auto_renew = false
                      AND c.ends_at IS NOT NULL
                      AND c.ends_at::date = (CURRENT_DATE + CAST(:days AS int))
                      -- Anti-doublon au niveau (contrat × jalon), PAS par destinataire : 1 seule alerte
                      -- pour le groupe admin (parité de la sémantique broadcast legacy admin_notifications).
                      AND NOT EXISTS (
                          SELECT 1 FROM notifications n
                          WHERE n.metadata->>'kind' = 'contract_expiring'
                            AND n.metadata->>'contractId' = c.id::text
                            AND n.metadata->>'milestone' = :milestone
                      )
                    """)
                .setParameter("days", days)
                .setParameter("milestone", milestone)
                .getResultList();
            for (Object[] row : due) {
                UUID contractId = toUuid(row[0]);
                UUID restaurantId = row[1] != null ? toUuid(row[1]) : null;
                String number = row[2] != null ? String.valueOf(row[2]) : null;
                String restoName = row[3] != null ? String.valueOf(row[3]) : null;
                String label = String.valueOf(row[4]);
                events.publishEvent(new ContractExpiringSoonEvent(
                    contractId, restaurantId, number, restoName, label, milestone, admins, Instant.now()));
                published++;
            }
        }
        log.info("[cron] alertExpiringContracts done: {} alerts published", published);
    }

    /** Colonne {@code uuid} d'une requête native : le driver pg renvoie un {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
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
