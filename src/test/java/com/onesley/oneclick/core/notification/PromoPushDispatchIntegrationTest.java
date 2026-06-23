package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.PromoRequestCreateDto;
import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.PromoRequestDto;
import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.PromoRequestReviewDto;
import com.onesley.oneclick.core.notification.internal.PromoNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Intégration R2 — l'approbation d'une demande de push promo déclenche tout le pipeline
 * event-driven cross-module : {@code review(approved)} → {@code PromoApprovedEvent} →
 * loyalty résout l'audience (segment {@code all}, SQL réel sur {@code loyalty_accounts}) →
 * {@code PromoAudienceResolvedEvent} → notification {@code sendPromo} (stub FCM en test) +
 * {@code markSent}. Preuve = la demande passe au statut {@code sent} + {@code pushSentAt} renseigné.
 */
class PromoPushDispatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired PromoNotificationService promoService;

    @Test
    void approvingPromoRequest_runsFullCrossModuleDispatch_untilStatusSent() throws Exception {
        List<Map<String, Object>> restos =
            jdbc.queryForList("SELECT id, tenant_id FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID client = SEED_SUPERADMIN_ID;

        // Garantit ≥ 1 compte fidélité sur ce resto (segment "all" doit cibler ce client).
        Integer existing = jdbc.queryForObject(
            "SELECT count(*) FROM loyalty_accounts WHERE client_id = ? AND restaurant_id = ? AND deleted_at IS NULL",
            Integer.class, client, restaurantId);
        UUID seededAccountId = null;
        if (existing != null && existing == 0) {
            seededAccountId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO loyalty_accounts (id, client_id, restaurant_id, balance, tenant_id)
                VALUES (?, ?, ?, 0, ?)""", seededAccountId, client, restaurantId, tenantId);
        }

        UUID requestId = null;
        try {
            PromoRequestDto created = promoService.create(new PromoRequestCreateDto(
                tenantId, restaurantId, null, "Promo test R2", "-20% ce week-end", "all", client));
            requestId = created.id();
            assertThat(created.status()).isEqualTo("pending");

            // Approbation → déclenche le pipeline (2 listeners @ApplicationModuleListener after-commit).
            promoService.review(requestId, new PromoRequestReviewDto("approved", client, null));

            boolean sent = false;
            for (int i = 0; i < 50 && !sent; i++) {
                String status = promoService.findById(requestId).status();
                if ("sent".equals(status)) { sent = true; break; }
                Thread.sleep(200);
            }
            PromoRequestDto after = promoService.findById(requestId);
            assertThat(sent).as("la demande passe à 'sent' après le dispatch cross-module").isTrue();
            assertThat(after.pushSentAt()).isNotNull();
        } finally {
            if (requestId != null) jdbc.update("DELETE FROM promo_notification_requests WHERE id = ?", requestId);
            if (seededAccountId != null) jdbc.update("DELETE FROM loyalty_accounts WHERE id = ?", seededAccountId);
        }
    }
}
