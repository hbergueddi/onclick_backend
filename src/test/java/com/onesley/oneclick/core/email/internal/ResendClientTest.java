package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ResendClient} — garde stub (sans clé) + résolution de marque
 * par tenant (toutes branches du switch via réflexion). L'envoi HTTP Resend réel relève
 * d'un test d'intégration externe (service tiers).
 */
class ResendClientTest {

    private EmailSendDto dto(String slug) {
        return new EmailSendDto("welcome", slug, List.of("a@x.ma"), "Sujet FR", null, Map.of());
    }

    private ResendClient newClient() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(mock(RestClient.class));
        return new ResendClient(builder);
    }

    @Test
    void send_noApiKey_stubMode() {
        // apiKey défaut = "" (non injecté en unit) → branche stub, pas d'appel réseau
        EmailSendResultDto res = newClient().send(dto("oneclick"), "<html/>");
        assertThat(res.sent()).isFalse();
        assertThat(res.error()).contains("not configured");
    }

    @Test
    void resolveFromBrand_allBranches() throws Exception {
        ResendClient client = newClient();
        ReflectionTestUtils.setField(client, "defaultFrom", "OneClick <noreply@app-oneclick.net>");
        Method m = ResendClient.class.getDeclaredMethod("resolveFromBrand", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(client, "palmeraie")).isEqualTo("PCC <noreply@app-oneclick.net>");
        assertThat(m.invoke(client, "PCC")).isEqualTo("PCC <noreply@app-oneclick.net>");          // case-insensitive + alias
        assertThat(m.invoke(client, "homu")).isEqualTo("HOMU <noreply@app-oneclick.net>");
        assertThat(m.invoke(client, "restopro")).isEqualTo("Restopro <noreply@app-oneclick.net>");
        assertThat(m.invoke(client, "oneclick")).isEqualTo("OneClick <noreply@app-oneclick.net>"); // default
        assertThat(m.invoke(client, (Object) null)).isEqualTo("OneClick <noreply@app-oneclick.net>"); // null → default
        assertThat(m.invoke(client, "inconnu")).isEqualTo("OneClick <noreply@app-oneclick.net>");   // unknown → default
    }
}
