package com.onesley.oneclick.core.email.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public final class EmailDtos {

    private EmailDtos() {}

    /**
     * Requête d'envoi email branded.
     *
     * @param template       nom du template (ex: "pcc-enrollment-invite", "homu-enrollment-invite",
     *                       "store-onboarding-decision-approved", "store-onboarding-decision-rejected",
     *                       "pcc-feedback-thread")
     * @param tenantSlug     branding tenant (ex: "palmeraie", "homu", "oneclick", "restopro")
     * @param to             liste destinataires
     * @param subjectFr      objet email (français)
     * @param subjectEn      objet email (anglais — optionnel)
     * @param variables      variables interpolées dans le template ({{firstName}}, {{link}}, etc.)
     */
    public record EmailSendDto(
        @NotBlank String template,
        @NotBlank String tenantSlug,
        @NotEmpty List<@Email String> to,
        @NotBlank String subjectFr,
        String subjectEn,
        Map<String, Object> variables
    ) {}

    public record EmailSendResultDto(
        boolean sent,
        Integer deliveredCount,
        String providerMessageId,
        String error
    ) {}
}
