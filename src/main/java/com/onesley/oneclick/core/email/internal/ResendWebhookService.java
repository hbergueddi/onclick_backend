package com.onesley.oneclick.core.email.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ingestion des webhooks Resend (Gap #4) — events {@code email.bounced} /
 * {@code email.complained} → suppression list via {@link EmailBounceService}.
 *
 * <p>Sécurité : signature Svix HMAC-SHA256 sur {@code "{svix-id}.{svix-timestamp}.{body}"}
 * (secret {@code whsec_...} base64-décodé). Si {@code app.email.resend.webhook-secret}
 * est absent → mode stub (on accepte sans vérifier, log warn) pour les environnements
 * de test ; en prod le secret est configuré et toute signature invalide est rejetée (403).
 *
 * <p>Port de l'EF legacy {@code resend-webhooks} (4Click).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResendWebhookService {

    @Value("${app.email.resend.webhook-secret:}")
    private String webhookSecret;

    private final EmailBounceService bounceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Traite un webhook Resend : vérifie la signature, parse l'event, enregistre les bounces.
     *
     * @return nombre d'adresses enregistrées (0 pour les events no-op delivered/opened/…)
     * @throws ForbiddenException (403) si la signature est invalide alors qu'un secret est configuré
     */
    public int handle(String body, String svixId, String svixTimestamp, String svixSignature) {
        verifySignature(body, svixId, svixTimestamp, svixSignature);

        final JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("[email/resend-webhook] payload illisible: {}", e.getMessage());
            return 0;
        }

        String type = event.path("type").asText("");
        // bounce.type Resend : "Permanent" | "Transient" | "Undetermined"
        String bounceTypeRaw = event.path("data").path("bounce").path("type").asText("");
        String mapped = switch (type) {
            case "email.bounced" -> "Permanent".equalsIgnoreCase(bounceTypeRaw) ? "permanent" : "transient";
            case "email.complained" -> "complaint";
            default -> null; // delivered / sent / opened / clicked → no-op
        };
        if (mapped == null) return 0;

        String reason = event.path("data").path("bounce").path("subType").asText(null);
        JsonNode recipients = event.path("data").path("to");
        int count = 0;
        if (recipients.isArray()) {
            for (JsonNode r : recipients) {
                bounceService.recordBounce(r.asText(), mapped, reason, "resend-webhook", body);
                count++;
            }
        }
        return count;
    }

    /**
     * Vérifie la signature Svix. No-op (log warn) si aucun secret n'est configuré.
     * Format header : {@code "v1,sig1 v1,sig2 ..."} ; base attendue {@code id.ts.body}.
     */
    private void verifySignature(String body, String svixId, String svixTimestamp, String svixSignature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[email/resend-webhook] webhook-secret absent — signature non vérifiée (stub/test)");
            return;
        }
        if (svixId == null || svixTimestamp == null || svixSignature == null) {
            throw new ForbiddenException("Signature webhook manquante");
        }
        try {
            String secretB64 = webhookSecret.startsWith("whsec_") ? webhookSecret.substring(6) : webhookSecret;
            byte[] key = Base64.getDecoder().decode(secretB64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String signedContent = svixId + "." + svixTimestamp + "." + body;
            String expected = Base64.getEncoder().encodeToString(
                mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
            boolean ok = false;
            for (String part : svixSignature.split(" ")) {
                String sig = part.contains(",") ? part.substring(part.indexOf(',') + 1) : part;
                if (constantTimeEquals(sig, expected)) { ok = true; break; }
            }
            if (!ok) throw new ForbiddenException("Signature webhook invalide");
        } catch (ForbiddenException fe) {
            throw fe;
        } catch (Exception e) {
            throw new ForbiddenException("Vérification de signature impossible: " + e.getMessage());
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
