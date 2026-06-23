package com.onesley.oneclick.modules;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.financial.internal.FinancialCronJobs;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyCronJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Intégration Features A/B (alertes pré-expiration) — les crons exécutent leur SQL natif contre le
 * schéma réel <b>et</b> déclenchent la chaîne event → listener → notif in-app (avec metadata
 * anti-doublon). Valide aussi le fix {@code renewContracts} (respect du flag {@code auto_renew}).
 *
 * <p>FCM est en mode stub dans le profil de test ({@code app.fcm.*} vides) ; on valide la chaîne
 * backend (cron → event → {@code NotificationEventHandler} → {@code NotificationService}).
 */
class ExpiryAlertCronIntegrationTest extends AbstractIntegrationTest {

    @Autowired LoyaltyCronJobs loyaltyCron;
    @Autowired FinancialCronJobs financialCron;

    @Test
    void crons_executeAgainstRealSchema_noException() {
        // Valide les requêtes natives (CAST jalons, JOIN, anti-doublon, auto_renew) sur le schéma réel.
        assertThatCode(() -> {
            loyaltyCron.alertExpiringPoints();
            financialCron.alertExpiringContracts();
        }).doesNotThrowAnyException();
    }

    @Test
    void alertExpiringPoints_seededEarnAtJ7_createsOneNotif_idempotentOnRerun() throws Exception {
        UUID client = SEED_SUPERADMIN_ID;
        // Resto + tenant seedés, SANS compte loyalty existant pour ce client (évite tout conflit UNIQUE).
        List<Map<String, Object>> restos = jdbc.queryForList("""
            SELECT r.id, r.tenant_id FROM restaurants r
            WHERE r.tenant_id IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM loyalty_accounts la WHERE la.restaurant_id = r.id AND la.client_id = ?)
            LIMIT 1
            """, client);
        assumeThat(restos).as("un restaurant seedé sans compte loyalty pour le client de test").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID accountId = UUID.randomUUID();
        UUID earnId = UUID.randomUUID();

        jdbc.update("INSERT INTO loyalty_accounts (id, client_id, restaurant_id, tenant_id, balance) VALUES (?,?,?,?,?)",
            accountId, client, restaurantId, tenantId, 120);
        jdbc.update("""
            INSERT INTO loyalty_transactions (id, account_id, type, points, expires_at)
            VALUES (?, ?, 'earn', 120, (CURRENT_DATE + INTERVAL '7 days'))
            """, earnId, accountId);

        String countSql = """
            SELECT count(*) FROM notifications
            WHERE recipient_user_id = ? AND type = 'loyalty'
              AND metadata->>'kind' = 'points_expiring'
              AND metadata->>'accountId' = ? AND metadata->>'milestone' = 'j7'
            """;
        try {
            // 1re run : la notif est créée par le listener (async after-commit → poll).
            loyaltyCron.alertExpiringPoints();
            long n1 = 0;
            for (int i = 0; i < 40; i++) {
                n1 = jdbc.queryForObject(countSql, Long.class, client, accountId.toString());
                if (n1 >= 1) break;
                Thread.sleep(200);
            }
            assertThat(n1).as("1re run : notif loyalty 'points_expiring' j7 créée").isEqualTo(1L);

            // 2e run le même jour : l'anti-doublon (NOT EXISTS notifications.metadata) doit éviter toute 2e notif.
            loyaltyCron.alertExpiringPoints();
            Thread.sleep(1500); // laisse le temps à un éventuel listener async de fire (il ne doit PAS).
            long n2 = jdbc.queryForObject(countSql, Long.class, client, accountId.toString());
            assertThat(n2).as("2e run : anti-doublon → toujours 1 notif").isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ? AND metadata->>'accountId' = ?",
                client, accountId.toString());
            jdbc.update("DELETE FROM loyalty_transactions WHERE id = ?", earnId);
            jdbc.update("DELETE FROM loyalty_accounts WHERE id = ?", accountId);
        }
    }

    @Test
    void alertExpiringContracts_autoRenewFalseAtJ7_alertsAdmin_andRenewLeavesItUntouched() throws Exception {
        List<Map<String, Object>> restos =
            jdbc.queryForList("SELECT id, tenant_id FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID contractId = UUID.randomUUID();
        String number = "TEST-EXP-" + contractId.toString().substring(0, 8);

        jdbc.update("""
            INSERT INTO contracts (id, restaurant_id, contract_number, commission_rate, starts_at, tenant_id,
                                   status, auto_renew, ends_at)
            VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 'active', false, (CURRENT_DATE + INTERVAL '7 days'))
            """, contractId, restaurantId, number, new BigDecimal("3"), tenantId);

        try {
            // 1. renewContracts ne doit PAS toucher un contrat auto_renew=false (même dans la fenêtre 30j).
            financialCron.renewContracts();
            String endsAt = jdbc.queryForObject(
                "SELECT ends_at::date::text FROM contracts WHERE id = ?", String.class, contractId);
            assertThat(endsAt).as("auto_renew=false → non renouvelé par renewContracts")
                .isEqualTo(LocalDate.now().plusDays(7).toString());

            // 2. alertExpiringContracts alerte l'admin (SUPERADMIN) au jalon j7.
            String countSql = """
                SELECT count(*) FROM notifications
                WHERE type = 'system'
                  AND metadata->>'kind' = 'contract_expiring'
                  AND metadata->>'contractId' = ? AND metadata->>'milestone' = 'j7'
                """;
            financialCron.alertExpiringContracts();
            long n1 = 0;
            for (int i = 0; i < 40; i++) {
                n1 = jdbc.queryForObject(countSql, Long.class, contractId.toString());
                if (n1 >= 1) break;
                Thread.sleep(200);
            }
            assertThat(n1).as("1re run : notif system 'contract_expiring' j7 créée pour l'admin").isEqualTo(1L);

            // 3. 2e run le même jour : anti-doublon → pas de 2e notif (1 SUPERADMIN seedé).
            financialCron.alertExpiringContracts();
            Thread.sleep(1500);
            long n2 = jdbc.queryForObject(countSql, Long.class, contractId.toString());
            assertThat(n2).as("2e run : anti-doublon → toujours 1 notif").isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM notifications WHERE metadata->>'contractId' = ?", contractId.toString());
            jdbc.update("DELETE FROM contracts WHERE id = ?", contractId);
        }
    }

    @Test
    void renewContracts_autoRenewTrueWithin30d_isRenewed() {
        List<Map<String, Object>> restos =
            jdbc.queryForList("SELECT id, tenant_id FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID contractId = UUID.randomUUID();
        String number = "TEST-RNW-" + contractId.toString().substring(0, 8);

        jdbc.update("""
            INSERT INTO contracts (id, restaurant_id, contract_number, commission_rate, starts_at, tenant_id,
                                   status, auto_renew, ends_at)
            VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 'active', true, (CURRENT_DATE + INTERVAL '10 days'))
            """, contractId, restaurantId, number, new BigDecimal("3"), tenantId);

        try {
            // Contrôle positif : le happy path d'auto-renouvellement marche toujours après le fix.
            financialCron.renewContracts();
            String endsAt = jdbc.queryForObject(
                "SELECT ends_at::date::text FROM contracts WHERE id = ?", String.class, contractId);
            // +1 an depuis (aujourd'hui + 10j) → bien au-delà de 300 jours.
            assertThat(LocalDate.parse(endsAt))
                .as("auto_renew=true → prolongé +1 an")
                .isAfter(LocalDate.now().plusDays(300));
        } finally {
            jdbc.update("DELETE FROM contracts WHERE id = ?", contractId);
        }
    }
}
