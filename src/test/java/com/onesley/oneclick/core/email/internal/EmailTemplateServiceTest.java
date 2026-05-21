package com.onesley.oneclick.core.email.internal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link EmailTemplateService} (L3 — core.email).
 * Template trouvé (interpolation + escaping + variable absente) vs stub (template absent).
 * Fixture : {@code src/test/resources/email-templates/default/test-welcome.html}.
 */
class EmailTemplateServiceTest {

    private final EmailTemplateService service = new EmailTemplateService();

    @Test
    void render_templateFound_interpolatesEscapesAndLeavesUnknown() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Ali & <b>");   // doit être échappé
        vars.put("code", 1234);
        String html = service.render("tenant-inexistant", "test-welcome", vars, "Bienvenue");
        // tenant introuvable → fallback default/test-welcome.html
        assertThat(html).contains("Ali &amp; &lt;b&gt;");
        assertThat(html).contains("votre code est 1234");
        assertThat(html).contains("{{absent}}"); // variable non fournie → laissée intacte
    }

    @Test
    void render_templateNotFound_stubWithVariables() {
        Map<String, Object> vars = Map.of("ref", "OC-42");
        String html = service.render("x", "template-qui-nexiste-pas", vars, "Sujet <Test>");
        assertThat(html).contains("Sujet &lt;Test&gt;"); // subject échappé dans le stub
        assertThat(html).contains("OC-42");
        assertThat(html).contains("L'équipe OneClick");
    }

    @Test
    void render_templateNotFound_stubNullVariables() {
        String html = service.render("x", "absent", null, "Sujet simple");
        assertThat(html).contains("Sujet simple");
        assertThat(html).doesNotContain("<ul>"); // pas de variables → pas de liste
    }
}
