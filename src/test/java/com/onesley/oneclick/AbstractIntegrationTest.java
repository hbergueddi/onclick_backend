package com.onesley.oneclick;

import com.onesley.oneclick.security.JwtIssuer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Base class for module integration smoke tests.
 *
 * <p>Boots the full Spring context against {@code oneclick_enterprise}
 * (profile {@code enterprise}). On active OAuth2 en mode "réel" mais avec un
 * secret HS256 partagé entre {@link JwtIssuer} (émission) et le
 * {@link com.onesley.oneclick.security.JwtConfig} (validation). Chaque sous-classe
 * récupère un Bearer signé via {@link #adminBearer()} (rôle SUPERADMIN chargé
 * depuis la DB via {@link com.onesley.oneclick.security.UserRoleAuthoritiesConverter}).
 *
 * <p>Pourquoi pas {@code app.security.oauth2.enabled=false} + {@code @WithMockUser} :
 * On passe par la stack HTTP réelle (vraie URL + port aléatoire), donc le
 * {@code SecurityContextHolder} reste vide et les {@code @PreAuthorize("isAuthenticated()")}
 * répondent 403. Avec OAuth2 enabled + JWT signé HS256 + user en DB, on couvre
 * vraiment le pipeline : filter chain → JwtDecoder → UserRoleAuthoritiesConverter
 * → controller → service → repo → DB.
 *
 * <p>Spring Boot 4 a retiré {@code TestRestTemplate} → on utilise un
 * {@link RestTemplate} configuré pour ne pas lever sur les status d'erreur
 * (on assert dessus dans chaque test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("enterprise")
@TestPropertySource(properties = {
    "spring.modulith.events.externalization.enabled=false",
    "spring.kafka.bootstrap-servers=",
    "app.security.oauth2.enabled=true",
    "app.security.jwt-secret=onesley-oneclick-dev-secret-256-bits-minimum-length-required"
})
public abstract class AbstractIntegrationTest {

    /**
     * SUPERADMIN seedé dans {@code oneclick_enterprise} — on s'en sert pour signer
     * un JWT dont le {@code sub} pointe sur un user existant avec un rôle réel.
     */
    protected static final UUID SEED_SUPERADMIN_ID =
        UUID.fromString("b5637379-54be-442a-9cf6-9c3a2cc23c4a");

    @LocalServerPort
    protected int port;

    @Autowired
    protected JwtIssuer jwtIssuer;

    /** RestTemplate qui ne throw pas sur 4xx/5xx — on test sur le status code. */
    protected final RestTemplate restTemplate = buildLenientRestTemplate();

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Build HttpEntity with optional JWT bearer. */
    protected HttpEntity<?> jwtEntity(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) headers.setBearerAuth(jwt);
        return new HttpEntity<>(headers);
    }

    /** Build HttpEntity with bearer + JSON body. */
    protected <T> HttpEntity<T> jsonJwtEntity(T body, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) headers.setBearerAuth(jwt);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** Token SUPERADMIN signé HS256 pour les routes protégées (@PreAuthorize). */
    protected String adminBearer() {
        return jwtIssuer.issueAccessToken(SEED_SUPERADMIN_ID, "SUPERADMIN").token();
    }

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    protected org.springframework.transaction.PlatformTransactionManager txManager;

    /** Baseline temporelle pour scoper le nettoyage des events Modulith créés par le test. */
    private java.time.Instant eventPublicationBaseline;

    @org.junit.jupiter.api.BeforeEach
    void captureEventPublicationBaseline() {
        eventPublicationBaseline = java.time.Instant.now();
    }

    /**
     * Isolation events (racine systémique) : clôt les publications Spring Modulith créées PENDANT ce
     * test (scopées {@code publication_date >= baseline}) pour qu'elles ne restent pas <b>incomplètes</b>
     * dans la DB partagée {@code oneclick_enterprise}. Sans ça, ces lignes étaient rejouées au boot du
     * backend live (profil enterprise, {@code republish-outstanding-events-on-restart=true}) et
     * provoquaient des violations de FK en boucle (listener référençant une donnée de test supprimée).
     * Scopé à la fenêtre du test → ne touche aucune ligne préexistante (live/autres).
     */
    @org.junit.jupiter.api.AfterEach
    void completeTestEventPublications() {
        if (eventPublicationBaseline == null) return;
        // L'UPDATE de complétion s'exécute dans une transaction NEUVE en lecture-écriture
        // (REQUIRES_NEW, readOnly=false) : il ne doit PAS hériter d'un éventuel
        // @Transactional(readOnly = true) posé sur la méthode de test — sinon SQLSTATE 25006
        // « cannot execute UPDATE in a read-only transaction » (cf SystemHealthDashboardPublisherIntegrationTest).
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tx.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setReadOnly(false);
        tx.executeWithoutResult(status -> jdbc.update(
            "UPDATE event_publication SET completion_date = now() "
            + "WHERE completion_date IS NULL AND publication_date >= ?",
            java.sql.Timestamp.from(eventPublicationBaseline)));
    }

    /**
     * Bearer signé pour un user RÉEL du rôle donné (lookup DB). Les authorities
     * ne viennent PAS du claim {@code role} mais du graphe role→permissions chargé
     * en DB par {@link com.onesley.oneclick.security.UserRoleAuthoritiesConverter} —
     * d'où un user existant requis (CLIENT, RESTAURATEUR, GROUP_ADMIN seedés).
     *
     * <p><b>Déterminisme + tenant canonique</b> : la sélection est ordonnée pour (a) préférer le
     * tenant {@code oneclick} (tenant de référence des tests) puis (b) trancher par {@code id}. Sans
     * cet {@code ORDER BY}, {@code LIMIT 1} renvoyait un user arbitraire (ordre de scan Postgres) :
     * sur 17050 RESTAURATEUR seedés, 20 ne sont PAS oneclick → les tests tenant-scopés (gestion du
     * parc {@code resource_bookings}, no-show-stats…) tombaient en 404/scope vide de façon flaky
     * selon les écritures des tests précédents. Préférer oneclick garantit que le user résolu partage
     * le tenant des fixtures créées sous {@code oneclick}.</p>
     */
    protected String bearerForRole(String roleCode) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = ? AND u.deleted_at IS NULL "
            + "ORDER BY (u.tenant_id = (SELECT id FROM tenants WHERE slug = 'oneclick' "
            + "AND deleted_at IS NULL)) DESC NULLS LAST, u.id "
            + "LIMIT 1", String.class, roleCode);
        return jwtIssuer.issueAccessToken(java.util.UUID.fromString(id), roleCode).token();
    }

    private static RestTemplate buildLenientRestTemplate() {
        // JDK HttpClient (pas HttpURLConnection) : supporte PATCH, utilisé par
        // de nombreux endpoints OneClick. SimpleClientHttpRequestFactory lève
        // "Invalid HTTP method: PATCH".
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                // Ne jamais throw — on assert sur le status dans le test.
                return false;
            }
        });
        return rt;
    }
}
