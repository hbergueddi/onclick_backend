package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service Sprint I.3 — rendu des templates HTML email.
 *
 * <p>Les templates vivent dans {@code src/main/resources/email-templates/{tenantSlug}/{template}.html}
 * avec fallback {@code email-templates/default/{template}.html}.
 *
 * <p>Interpolation : remplace {{variable}} par la valeur du Map. Variables non
 * fournies → laissées intactes (log warning).
 *
 * <p>Template stub si aucun fichier trouvé : génère un HTML générique avec
 * subject + variables formatées (pour MVP — pas de surprise UX si le branding
 * n'est pas encore livré).
 */
@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    public String render(String tenantSlug, String template, Map<String, Object> variables, String subjectFallback) {
        String html = loadTemplate(tenantSlug, template);
        if (html == null) {
            log.warn("[email/template] template not found tenant={} template={} — using stub",
                tenantSlug, template);
            html = buildStubHtml(subjectFallback, variables);
        }
        return interpolate(html, variables == null ? Map.of() : variables);
    }

    private String loadTemplate(String tenantSlug, String template) {
        String[] candidates = new String[]{
            "email-templates/" + tenantSlug + "/" + template + ".html",
            "email-templates/default/" + template + ".html"
        };
        for (String path : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (resource.exists()) {
                    return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.debug("[email/template] read failed {}: {}", path, e.getMessage());
            }
        }
        return null;
    }

    private String buildStubHtml(String subject, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"font-family:sans-serif;max-width:600px;margin:auto;padding:20px\">");
        sb.append("<h2>").append(escape(subject)).append("</h2>");
        sb.append("<p>Bonjour,</p>");
        if (variables != null && !variables.isEmpty()) {
            sb.append("<ul>");
            variables.forEach((k, v) -> sb.append("<li><b>").append(escape(k))
                .append("</b> : ").append(escape(String.valueOf(v))).append("</li>"));
            sb.append("</ul>");
        }
        sb.append("<p>Cordialement,<br/>L'équipe OneClick</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String interpolate(String template, Map<String, Object> variables) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value != null ? escape(String.valueOf(value)) : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
