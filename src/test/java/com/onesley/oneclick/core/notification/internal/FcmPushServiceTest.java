package com.onesley.oneclick.core.notification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests unitaires ISOLÉS de {@link FcmPushService} (Sprint B.8.4).
 *
 * <p>Aucun appel réseau réel : le couple OAuth Google + FCM HTTP v1 est intercepté par
 * {@link MockRestServiceServer} (lié au {@code RestClient.Builder} injecté). La signature
 * JWT RS256 est vérifiée cryptographiquement avec la clé publique de la paire de test.
 *
 * <p>Couvre : mode stub (non configuré), JWT signé/claims, fan-out succès (1 et N tokens),
 * cleanup d'un token périmé (FCM {@code UNREGISTERED}), et échec d'acquisition OAuth.
 */
class FcmPushServiceTest {

    private static final String CLIENT_EMAIL = "firebase-adminsdk@oneclick-129bb.iam.gserviceaccount.com";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/oneclick-129bb/messages:send";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private static KeyPair keys;
    private static String saJson;

    private final DeviceTokenRepository tokenRepo = mock(DeviceTokenRepository.class);
    private final NotificationRepository notifRepo = mock(NotificationRepository.class);

    @BeforeAll
    static void generateServiceAccount() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keys = kpg.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        saJson = new ObjectMapper().writeValueAsString(Map.of(
            "client_email", CLIENT_EMAIL,
            "private_key", pem,
            "token_uri", TOKEN_URI,
            "project_id", "oneclick-129bb"
        ));
    }

    /** Service configuré + mock du couple HTTP (OAuth + FCM). */
    private record Sut(FcmPushService svc, MockRestServiceServer server) {}

    private Sut configured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // dispatch-enabled=true : on exerce le fan-out FCM réel (mock HTTP).
        FcmPushService svc = new FcmPushService("oneclick-129bb", saJson, true, builder, tokenRepo, notifRepo);
        return new Sut(svc, server);
    }

    private FcmPushService stub() {
        return new FcmPushService("", "", false, RestClient.builder(), tokenRepo, notifRepo);
    }

    /** Clés FCM présentes MAIS garde-fou dispatch désactivé → doit rester en stub (aucun HTTP). */
    private FcmPushService configuredButDispatchDisabled() {
        return new FcmPushService("oneclick-129bb", saJson, false, RestClient.builder(), tokenRepo, notifRepo);
    }

    private DeviceToken token(String t) {
        return new DeviceToken(UUID.randomUUID(), UUID.randomUUID(), t, "ios");
    }

    /** Matcher : extrait l'assertion JWT du form OAuth et vérifie signature + claims. */
    private void expectOAuthWithValidJwt(MockRestServiceServer server, String accessToken) {
        server.expect(requestTo(TOKEN_URI))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertThat(body).contains("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer");
                String assertion = body.substring(body.indexOf("assertion=") + "assertion=".length());
                try {
                    SignedJWT jwt = SignedJWT.parse(assertion);
                    assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())))
                        .as("JWT signé par la clé privée du service-account").isTrue();
                    JWTClaimsSet c = jwt.getJWTClaimsSet();
                    assertThat(c.getIssuer()).isEqualTo(CLIENT_EMAIL);
                    assertThat(c.getStringClaim("scope")).isEqualTo(SCOPE);
                    assertThat(c.getAudience()).contains(TOKEN_URI);
                    assertThat(c.getExpirationTime()).isAfter(new java.util.Date());
                } catch (Exception e) {
                    throw new AssertionError("JWT invalide: " + e.getMessage(), e);
                }
            })
            .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\"}", MediaType.APPLICATION_JSON));
    }

    // ─── Mode stub (non configuré) : aucun HTTP ────────────────────────────────

    @Test
    void isConfigured_trueWhenSet_falseWhenBlank() {
        assertThat(configured().svc().isConfigured()).isTrue();
        assertThat(stub().isConfigured()).isFalse();
    }

    @Test
    void sendPromo_notConfigured_stubNoHttp() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("t")));
        var res = stub().sendPromo(new PushPromoDto(UUID.randomUUID(), List.of(UUID.randomUUID()), "T", "B", "/l"));
        assertThat(res.sent()).isZero();
        assertThat(res.message()).isEqualTo("FCM not configured");
    }

    @Test
    void sendReservation_notConfigured_stubNoHttp() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("t")));
        var res = stub().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", "/l"));
        assertThat(res.message()).isEqualTo("FCM not configured");
        assertThat(res.sent()).isZero();
    }

    @Test
    void sendPromo_configuredButDispatchDisabled_stubNoHttp() {
        // Garde-fou : clés présentes mais app.fcm.dispatch-enabled=false → aucun envoi réel.
        // Pas de MockRestServiceServer → tout appel HTTP ferait planter le test (RestClient nu).
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("t")));
        var res = configuredButDispatchDisabled().sendPromo(
            new PushPromoDto(UUID.randomUUID(), List.of(UUID.randomUUID()), "T", "B", "/l"));
        assertThat(res.sent()).isZero();
        assertThat(res.message()).contains("dispatch disabled");
    }

    // ─── Fan-out réel (OAuth + FCM mockés) ─────────────────────────────────────

    @Test
    void sendReservation_configured_signsJwtAndSendsOneFcmPerToken() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("tok-1")));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "access-xyz");
        sut.server().expect(requestTo(FCM_URL))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer access-xyz"))
            .andExpect(content().string(containsString("\"token\":\"tok-1\"")))
            .andExpect(content().string(containsString("\"title\":\"Résa\"")))
            .andExpect(content().string(containsString("\"link\":\"/pocket/oneclick\"")))
            .andRespond(withSuccess());

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "Résa", "Confirmée", "/pocket/oneclick"));

        assertThat(res.sent()).isEqualTo(1);
        assertThat(res.total()).isEqualTo(1);
        assertThat(res.message()).isEqualTo("ok");
        sut.server().verify();
    }

    @Test
    void sendPromo_configured_fanOutAllTokens() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("a"), token("b")));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        sut.server().expect(requestTo(FCM_URL)).andRespond(withSuccess());
        sut.server().expect(requestTo(FCM_URL)).andRespond(withSuccess());

        var res = sut.svc().sendPromo(new PushPromoDto(
            UUID.randomUUID(), List.of(UUID.randomUUID()), "Promo", "-20%", null));

        assertThat(res.total()).isEqualTo(2);
        assertThat(res.sent()).isEqualTo(2);
        sut.server().verify();
    }

    @Test
    void fanOut_staleToken_isDeletedAndReportedAsFailure() {
        DeviceToken dead = token("dead-token");
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(dead));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        // Corps d'erreur FCM v1 canonique pour un token périmé : 404 + errorCode UNREGISTERED.
        sut.server().expect(requestTo(FCM_URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\","
                    + "\"message\":\"Requested entity was not found.\",\"details\":["
                    + "{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\","
                    + "\"errorCode\":\"UNREGISTERED\"}]}}")
                .contentType(MediaType.APPLICATION_JSON));

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));

        assertThat(res.sent()).isZero();
        verify(tokenRepo).delete(dead);                                   // cleanup token périmé
        assertThat(res.failures()).anyMatch(f -> f.contains("UNREGISTERED"));
        sut.server().verify();
    }

    @Test
    void fanOut_staleToken_cleanupDeleteThrows_doesNotPropagate() {
        // Si la suppression du token périmé échoue (ex: incident DB), le fan-out NE doit PAS propager
        // l'exception (sinon, appelé depuis une transaction async — PromoDispatchListener — le listener
        // Modulith planterait → event incomplet). On vérifie : pas d'exception, delete tenté, échec tracé.
        DeviceToken dead = token("dead-token");
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(dead));
        doThrow(new RuntimeException("delete boom")).when(tokenRepo).delete(dead);
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        sut.server().expect(requestTo(FCM_URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\",\"details\":["
                    + "{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\","
                    + "\"errorCode\":\"UNREGISTERED\"}]}}")
                .contentType(MediaType.APPLICATION_JSON));

        // Ne jette pas malgré l'échec de cleanup.
        var res = sut.svc().sendPromo(new PushPromoDto(
            UUID.randomUUID(), List.of(UUID.randomUUID()), "T", "B", null));

        assertThat(res.sent()).isZero();
        verify(tokenRepo).delete(dead);                                       // cleanup tenté
        assertThat(res.failures()).anyMatch(f -> f.contains("cleanup failed"));
        sut.server().verify();
    }

    @Test
    void fanOut_transientError_keepsTokenAndReportsFailure() {
        DeviceToken live = token("live-token");
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(live));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        // 503 UNAVAILABLE : erreur transitoire — le token reste valide, NE doit PAS être purgé.
        sut.server().expect(requestTo(FCM_URL))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\","
                    + "\"message\":\"The service is currently unavailable.\"}}")
                .contentType(MediaType.APPLICATION_JSON));

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));

        assertThat(res.sent()).isZero();
        verify(tokenRepo, never()).delete(any(DeviceToken.class));
        assertThat(res.failures()).anyMatch(f -> f.contains("HTTP 503"));
        sut.server().verify();
    }

    @Test
    void fanOut_404WithoutUnregistered_keepsToken() {
        DeviceToken live = token("live-token");
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(live));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        // 404 proxy/gateway (corps HTML, pas d'errorCode UNREGISTERED) : NE doit PAS purger le token.
        sut.server().expect(requestTo(FCM_URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("<html><body>404 Not Found</body></html>")
                .contentType(MediaType.TEXT_HTML));

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));

        assertThat(res.sent()).isZero();
        verify(tokenRepo, never()).delete(any(DeviceToken.class));
        sut.server().verify();
    }

    @Test
    void sendPromo_multipleUsers_fanOutOnePerUserToken() {
        UUID uidA = UUID.randomUUID();
        UUID uidB = UUID.randomUUID();
        when(tokenRepo.findAllByUserId(uidA)).thenReturn(List.of(token("a1")));
        when(tokenRepo.findAllByUserId(uidB)).thenReturn(List.of(token("b1")));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        sut.server().expect(requestTo(FCM_URL)).andRespond(withSuccess());
        sut.server().expect(requestTo(FCM_URL)).andRespond(withSuccess());

        var res = sut.svc().sendPromo(new PushPromoDto(
            UUID.randomUUID(), List.of(uidA, uidB), "Promo", "-20%", null));

        assertThat(res.total()).isEqualTo(2);     // 1 token par user résolu sur les 2 userIds
        assertThat(res.sent()).isEqualTo(2);
        sut.server().verify();
    }

    @Test
    void sendReservation_nullLink_omitsDataField() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("tok")));
        Sut sut = configured();
        expectOAuthWithValidJwt(sut.server(), "tk");
        sut.server().expect(requestTo(FCM_URL))
            .andExpect(content().string(not(containsString("\"data\""))))   // pas de data sans link
            .andRespond(withSuccess());

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));

        assertThat(res.sent()).isEqualTo(1);
        sut.server().verify();
    }

    @Test
    void fanOut_oauthFails_returnsAuthFailed_noFcmCall() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token("t")));
        Sut sut = configured();
        sut.server().expect(requestTo(TOKEN_URI))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var res = sut.svc().sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));

        assertThat(res.sent()).isZero();
        assertThat(res.message()).isEqualTo("FCM auth failed");
        verify(tokenRepo, never()).delete(any(DeviceToken.class));
        sut.server().verify();   // aucune requête FCM émise (OAuth échoué)
    }
}
