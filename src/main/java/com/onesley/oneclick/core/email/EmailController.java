package com.onesley.oneclick.core.email;

import com.onesley.oneclick.core.email.api.EmailDtos.*;
import com.onesley.oneclick.core.email.internal.EmailTemplateService;
import com.onesley.oneclick.core.email.internal.ResendClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
public class EmailController {

    private final ResendClient resendClient;
    private final EmailTemplateService templateService;

    public EmailController(ResendClient resendClient, EmailTemplateService templateService) {
        this.resendClient = resendClient;
        this.templateService = templateService;
    }

    // Bug 32 (Batch D RBAC v2) — RESOURCE=NOTIFICATIONS (email = canal notification).
    @PostMapping("/send")
    @Operation(summary = "Envoie un email branded (template + tenant slug + variables interpolées)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('CREATE:NOTIFICATIONS')")
    public ResponseEntity<EmailSendResultDto> send(@Valid @RequestBody EmailSendDto dto) {
        String html = templateService.render(dto.tenantSlug(), dto.template(),
            dto.variables(), dto.subjectFr());
        EmailSendResultDto result = resendClient.send(dto, html);
        HttpStatus status = result.sent() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }
}
