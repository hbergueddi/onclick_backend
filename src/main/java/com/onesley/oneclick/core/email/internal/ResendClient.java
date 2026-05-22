package com.onesley.oneclick.core.email.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.core.email.api.EmailDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Client Resend HTTP API — Sprint I.3.
 *
 * <p>Envoie un email branded via le provider Resend (https://resend.com/docs/api-reference).
 * Le template HTML est rendu en remplaçant les variables {{key}} à la volée.
 *
 * <p>Stub mode si {@code app.email.resend.api-key} absent : log + no-op safe.
 *
 * <p>Branding per-tenant : utilise un from-name + reply-to spécifique par slug
 * via {@code app.email.brand.{slug}.*}.
 */
@Component
@Slf4j
public class ResendClient {

    private static final String RESEND_API = "https://api.resend.com/emails";

    @Value("${app.email.resend.api-key:}")
    private String apiKey;

    @Value("${app.email.from.default:OneClick <noreply@app-oneclick.net>}")
    private String defaultFrom;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * RestClient construit depuis le {@code Builder} Spring auto-configuré (injectable
     * en test), aligné sur le pattern de {@code GooglePlacesEnrichmentService}.
     */
    public ResendClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * Envoi d'un email branded via Resend.
     *
     * @param dto      la requête d'envoi
     * @param htmlBody le HTML rendu (variables déjà interpolées)
     * @return résultat avec providerMessageId si succès
     */
    public EmailSendResultDto send(EmailSendDto dto, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[email/resend] api-key absent — stub mode (no send). To={}, subject={}",
                dto.to(), dto.subjectFr());
            return new EmailSendResultDto(false, 0, null, "RESEND_API_KEY not configured (stub)");
        }

        try {
            String from = resolveFromBrand(dto.tenantSlug());
            Map<String, Object> body = Map.of(
                "from", from,
                "to", dto.to(),
                "subject", dto.subjectFr(),
                "html", htmlBody
            );

            JsonNode resp = restClient.post()
                .uri(RESEND_API)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

            String messageId = resp != null && resp.has("id") ? resp.get("id").asText() : null;
            log.info("[email/resend] sent template={} tenant={} to={} messageId={}",
                dto.template(), dto.tenantSlug(), dto.to(), messageId);
            return new EmailSendResultDto(true, dto.to().size(), messageId, null);

        } catch (Exception e) {
            log.error("[email/resend] failed template={} tenant={}: {}",
                dto.template(), dto.tenantSlug(), e.getMessage(), e);
            return new EmailSendResultDto(false, 0, null, e.getMessage());
        }
    }

    /**
     * Résout l'expéditeur en fonction du tenant slug.
     *
     * <p>Branding par défaut :
     * <ul>
     *   <li>{@code palmeraie} → "PCC <noreply@palmeraie.oneclick.app>"</li>
     *   <li>{@code homu} → "HOMU <noreply@homu.oneclick.app>"</li>
     *   <li>{@code oneclick} (default) → "OneClick <noreply@app-oneclick.net>"</li>
     * </ul>
     */
    private String resolveFromBrand(String tenantSlug) {
        return switch (tenantSlug != null ? tenantSlug.toLowerCase() : "oneclick") {
            case "palmeraie", "pcc" -> "PCC <noreply@app-oneclick.net>";
            case "homu" -> "HOMU <noreply@app-oneclick.net>";
            case "restopro" -> "Restopro <noreply@app-oneclick.net>";
            default -> defaultFrom;
        };
    }
}
