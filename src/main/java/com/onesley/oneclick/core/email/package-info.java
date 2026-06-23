/**
 * Package {@code core/email} — wrapper Resend pour emails brandés whitelabel.
 *
 * <p>Sprint I.3 — création initiale (port EFs send-pcc-enrollment-invite,
 * send-homu-enrollment-invite, send-pcc-feedback-thread, etc.).
 *
 * <p>Pattern senior : 1 service {@code ResendClient} partagé + 1 endpoint
 * générique {@code POST /api/email/send} avec template name + variables.
 * Les templates HTML brandés vivent dans {@code src/main/resources/email-templates/}.
 *
 * <p>Stub mode si {@code app.email.resend.api-key} absent : logs + no-op.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "core.email",
    displayName = "core/email",
    allowedDependencies = {"core.identity", "core.tenant", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.core.email;

import org.springframework.modulith.ApplicationModule;
