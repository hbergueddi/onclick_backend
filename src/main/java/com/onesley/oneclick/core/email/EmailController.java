package com.onesley.oneclick.core.email;

import com.onesley.oneclick.core.email.api.EmailDtos.*;
import com.onesley.oneclick.core.email.internal.EmailBounceService;
import com.onesley.oneclick.core.email.internal.EmailTemplateService;
import com.onesley.oneclick.core.email.internal.ResendClient;
import com.onesley.oneclick.core.email.internal.ResendWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint Email Resend — Sprint I.3.
 *
 * <p>POST /api/email/send : envoie un email branded via Resend.
 *
 * <p>Pattern senior : 1 seul endpoint générique avec {@code template} + {@code tenantSlug}
 * + {@code variables}. Les EFs legacy {@code send-pcc-enrollment-invite},
 * {@code send-homu-enrollment-invite}, {@code send-pcc-feedback-thread}, etc.
 * sont remplacées par des appels à ce seul endpoint avec des templates différents.
 */
@RestController
@RequestMapping("/api/email")
@Tag(name = "Email", description = "Sprint I.3 — wrapper Resend pour emails brandés whitelabel")
@RequiredArgsConstructor
public class EmailController {

    private final ResendClient resendClient;
    private final EmailTemplateService templateService;
    private final ResendWebhookService webhookService;
    private final EmailBounceService bounceService;

    // Bug 32 (Batch D RBAC v2) — RESOURCE=NOTIFICATIONS (email = canal notification).
    @PostMapping("/send")
    @Operation(summary = "Envoie un email branded (template + tenant slug + variables interpolées)")
    @PreAuthorize("hasAuthority('CREATE:NOTIFICATIONS')")
    public ResponseEntity<EmailSendResultDto> send(@Valid @RequestBody EmailSendDto dto) {
        String html = templateService.render(dto.tenantSlug(), dto.template(),
            dto.variables(), dto.subjectFr());
        EmailSendResultDto result = resendClient.send(dto, html);
        HttpStatus status = result.sent() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Webhook Resend (Gap #4) — PUBLIC (whitelisté SecurityConfig). L'auth = la signature
     * Svix vérifiée par {@link ResendWebhookService} (403 si invalide quand un secret est
     * configuré). Alimente la suppression list (events bounced/complained).
     */
    @PostMapping("/webhooks/resend")
    @Operation(summary = "Webhook Resend — ingestion bounces/complaints (signature Svix)")
    public ResponseEntity<Map<String, Object>> resendWebhook(
        @RequestBody byte[] rawBody,
        @RequestHeader(value = "svix-id", required = false) String svixId,
        @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
        @RequestHeader(value = "svix-signature", required = false) String svixSignature
    ) {
        // byte[] : binding indépendant du Content-Type + corps brut byte-exact (signature Svix).
        String body = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        int recorded = webhookService.handle(body, svixId, svixTimestamp, svixSignature);
        return ResponseEntity.ok(Map.of("ok", true, "recorded", recorded));
    }

    @GetMapping("/bounces")
    @Operation(summary = "Liste des bounces emails (suppression list) — monitoring admin")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public List<EmailBounceDto> bounces() {
        return bounceService.listRecent();
    }
}
