package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/payment} — méthodes de paiement par user
 * + protections backend Bean Validation (Bug 36).
 */
class PaymentSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getMethodsByUser_returns200() {
        // SUPERADMIN admin → SecurityHelper.requireOwnerOrAdmin passe.
        // UUID inconnu → service renvoie liste vide (pas 404).
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/payments/methods/by-user/00000000-0000-0000-0000-000000000099"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Bug 36 — validation au niveau DTO ({@code @Pattern} sur {@code type}).
     * type hors liste autorisée → 400 (MethodArgumentNotValidException).
     */
    @Test
    void createMethod_invalidType_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", SEED_SUPERADMIN_ID.toString());
        body.put("type", "INVALID_TYPE_XYZ");

        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/payments/methods"), HttpMethod.POST,
            jsonJwtEntity(body, adminBearer()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Bug 36 — validation au niveau ENTITÉ ({@code @Size(max=4096)} sur
     * {@code provider_token}, colonne {@code text} sans limite DB). Le DTO ne
     * valide PAS ce champ → la violation surgit au flush Hibernate, emballée dans
     * une TransactionSystemException. Le GlobalExceptionHandler doit la déballer
     * en 400 (et non 500). Garantit que la protection backend donne une erreur
     * propre, pas une 500.
     */
    @Test
    void createMethod_oversizedEntityField_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", SEED_SUPERADMIN_ID.toString());
        body.put("type", "card");                 // type valide → passe le DTO
        body.put("providerToken", "x".repeat(5000)); // > @Size(max=4096) sur l'entité

        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/payments/methods"), HttpMethod.POST,
            jsonJwtEntity(body, adminBearer()), String.class);

        assertThat(response.getStatusCode())
            .as("violation @Size entité au flush → 400 propre (pas 500)")
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
