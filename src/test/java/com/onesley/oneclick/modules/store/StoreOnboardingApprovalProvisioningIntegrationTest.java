package com.onesley.oneclick.modules.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-2 + BE-3 (plan RESTAURANT-ONBOARDING) — E2E « approbation → provisioning → 1er login ».
 *
 * <p>Couvre, via la vraie stack HTTP : approbation admin d'une demande d'inscription →
 * (1) création du compte gérant (rôle RESTAURATEUR, {@code password_must_change=true}),
 * (2) création du restaurant + {@code restaurant_staffs(owner)},
 * (3) email d'approbation portant le mot de passe temporaire (capté via l'event),
 * (4) 1er login avec ce mdp → {@code passwordMustChange=true},
 * (5) changement de mot de passe → le drapeau tombe (re-login {@code passwordMustChange=false}).
 *
 * <p>Le mdp temporaire n'est jamais renvoyé par l'API (seulement dans l'email) : on le capte via
 * un listener synchrone {@link DecidedEventCapturer} sur {@link StoreOnboardingDecidedEvent}.
 * Nettoyage par soft-delete (évite les violations de FK + sort les entités des catalogues actifs).
 */
@Import(StoreOnboardingApprovalProvisioningIntegrationTest.DecidedEventCapturer.class)
class StoreOnboardingApprovalProvisioningIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    DecidedEventCapturer capturer;

    /** Capte l'event de décision (synchrone, dans la transaction) pour récupérer le mdp temporaire. */
    static class DecidedEventCapturer {
        final AtomicReference<StoreOnboardingDecidedEvent> last = new AtomicReference<>();
        @EventListener
        void on(StoreOnboardingDecidedEvent e) { last.set(e); }
    }

    private String oneclickTenantId() {
        return jdbc.queryForObject("SELECT id::text FROM tenants WHERE slug = 'oneclick' LIMIT 1", String.class);
    }
    private String anyUserId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    @Test
    void approval_provisionsOwnerAndRestaurant_andFirstLoginGate() throws Exception {
        capturer.last.set(null);
        String admin = adminBearer();
        String email = "onb-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma";

        // 1) Soumission de la demande (champ tenantId = tenant public oneclick).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", oneclickTenantId());
        body.put("restaurantName", "Resto BE2 Provisioning");
        body.put("city", "Casablanca");
        body.put("cuisine", "marocaine");
        body.put("ownerFirstName", "Owner");
        body.put("ownerLastName", "BE2");
        body.put("ownerEmail", email);
        String id = om.readTree(restTemplate.exchange(url("/api/store/onboarding"), HttpMethod.POST,
            jsonJwtEntity(body, admin), String.class).getBody()).get("id").asText();

        // 2) Approbation admin → provisioning synchrone (resto + compte gérant).
        ResponseEntity<String> dec = restTemplate.exchange(url("/api/store/onboarding/" + id + "/decision"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "approved", "reviewedBy", anyUserId()), admin), String.class);
        assertThat(dec.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode decNode = om.readTree(dec.getBody());
        String ownerUserId = decNode.get("provisionedUserId").asText();
        String restaurantId = decNode.get("provisionedRestaurantId").asText();
        assertThat(ownerUserId).isNotBlank().isNotEqualTo("null");
        assertThat(restaurantId).isNotBlank().isNotEqualTo("null");

        try {
            // 3) Event capté → mot de passe temporaire.
            StoreOnboardingDecidedEvent ev = capturer.last.get();
            assertThat(ev).as("StoreOnboardingDecidedEvent capté").isNotNull();
            assertThat(ev.approved()).isTrue();
            String tempPassword = ev.tempPassword();
            assertThat(tempPassword).as("mdp temporaire").isNotBlank();

            // 4) DB : compte gérant RESTAURATEUR + password_must_change=true + resto + staff(owner).
            assertThat(jdbc.queryForObject(
                "SELECT password_must_change FROM users WHERE id = ?::uuid", Boolean.class, ownerUserId)).isTrue();
            assertThat(jdbc.queryForObject(
                "SELECT r.code FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?::uuid", String.class, ownerUserId))
                .isEqualTo("RESTAURATEUR");
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM restaurants WHERE id = ?::uuid AND deleted_at IS NULL", Integer.class, restaurantId))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_staffs WHERE restaurant_id = ?::uuid AND user_id = ?::uuid AND role_code = 'owner'",
                Integer.class, restaurantId, ownerUserId)).isEqualTo(1);

            // 5) BE-3 gate : 1er login avec le mdp temporaire → passwordMustChange=true.
            ResponseEntity<String> login1 = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
                jsonJwtEntity(Map.of("email", email, "password", tempPassword), null), String.class);
            assertThat(login1.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode l1 = om.readTree(login1.getBody());
            assertThat(l1.get("passwordMustChange").asBoolean()).isTrue();
            String token = l1.get("accessToken").asText();

            // 6) Le gérant définit son mot de passe (changement avec le mdp temporaire courant).
            String newPw = "NouveauMdpBE2345";
            ResponseEntity<String> change = restTemplate.exchange(url("/api/users/me/password"), HttpMethod.POST,
                jsonJwtEntity(Map.of("currentPassword", tempPassword, "newPassword", newPw), token), String.class);
            assertThat(change.getStatusCode().is2xxSuccessful()).as("change password 2xx").isTrue();

            // 7) Re-login avec le nouveau mdp → le drapeau est tombé.
            ResponseEntity<String> login2 = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
                jsonJwtEntity(Map.of("email", email, "password", newPw), null), String.class);
            assertThat(login2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(login2.getBody()).get("passwordMustChange").asBoolean()).isFalse();
        } finally {
            // Nettoyage par soft-delete (FK-safe + hors catalogues actifs).
            jdbc.update("UPDATE restaurant_staffs SET deleted_at = now() WHERE restaurant_id = ?::uuid", UUID.fromString(restaurantId));
            jdbc.update("UPDATE restaurants SET deleted_at = now() WHERE id = ?::uuid", UUID.fromString(restaurantId));
            jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?::uuid", UUID.fromString(ownerUserId));
            jdbc.update("DELETE FROM store_onboarding_requests WHERE id = ?::uuid", UUID.fromString(id));
        }
    }
}
