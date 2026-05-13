package com.onesley.oneclick.security.vault;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EnvironmentPostProcessor Sprint I.8 — charge les secrets HashiCorp Vault
 * dans le Spring Environment AVANT que les beans soient instanciés.
 *
 * <p>Activé seulement si le profile {@code vault} est actif. Lit les secrets
 * KV v2 depuis {@code /v1/secret/data/oneclick} et les expose comme un
 * PropertySource haute-priorité (avant {@code application*.yml}).
 *
 * <p>Pourquoi pas Spring Cloud Vault Config :
 * <ul>
 *   <li>Spring Cloud 4.x ne supporte pas encore Spring Boot 4
 *       ({@code WebServerInitializedEvent} renommé)</li>
 *   <li>Notre besoin = injecter quelques dizaines de secrets une fois au boot,
 *       sans renouvellement automatique de lease (le mode dev a un root token
 *       éternel ; en prod on utilisera AppRole + restart Spring si rotation)</li>
 * </ul>
 *
 * <p>Si Vault est down ou inaccessible : log warning + skip (fail-safe en dev).
 * En prod, mettre {@code app.vault.fail-fast=true} pour bloquer le boot.
 *
 * <p>Configuration (par env vars) :
 * <ul>
 *   <li>{@code VAULT_URI}      — URL Vault (default {@code http://localhost:8200})</li>
 *   <li>{@code VAULT_TOKEN}    — token d'accès (default {@code oneclick-dev-root})</li>
 *   <li>{@code VAULT_PATH}     — KV v2 secret path (default {@code secret/data/oneclick})</li>
 *   <li>{@code VAULT_FAIL_FAST} — bloque le boot si Vault down (default false)</li>
 * </ul>
 */
public class VaultSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Nom du PropertySource injecté — visible dans /actuator/env. */
    private static final String PROPERTY_SOURCE_NAME = "vault-secrets";

    static {
        // Debug : prouve que le PostProcessor est découvert par Spring Boot.
        System.err.println("[vault] VaultSecretsEnvironmentPostProcessor LOADED");
    }

    @Override
    public int getOrder() {
        // Haut placé : avant application*.yml → Spring résoudra les ${db.username}
        // depuis Vault d'abord, puis fallback yml.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.err.println("[vault] postProcessEnvironment called");
        // Active si profile `vault` listé. Spring lit les profiles soit via
        // environment.getActiveProfiles() (déjà parsés), soit via la property
        // brute spring.profiles.active (avant le parsing officiel).
        String[] activeProfiles = environment.getActiveProfiles();
        String raw = environment.getProperty("spring.profiles.active", "");
        boolean vaultActive = Arrays.stream(activeProfiles).anyMatch("vault"::equalsIgnoreCase)
            || Arrays.stream(raw.split(",")).anyMatch(p -> "vault".equalsIgnoreCase(p.trim()));
        System.err.println("[vault] activeProfiles=" + Arrays.toString(activeProfiles)
            + " raw=" + raw + " vaultActive=" + vaultActive);
        if (!vaultActive) {
            return;
        }
        System.err.println("[vault] profile vault active — fetching secrets...");

        String uri = environment.getProperty("VAULT_URI", "http://localhost:8200");
        String token = environment.getProperty("VAULT_TOKEN", "oneclick-dev-root");
        String path = environment.getProperty("VAULT_PATH", "secret/data/oneclick");
        boolean failFast = Boolean.parseBoolean(environment.getProperty("VAULT_FAIL_FAST", "false"));

        try {
            Map<String, Object> secrets = fetchSecrets(uri, token, path);
            if (secrets.isEmpty()) {
                System.err.println("[vault] No secrets at " + path + " — skip (fail-fast=" + failFast + ")");
                if (failFast) {
                    throw new IllegalStateException("Vault empty path " + path);
                }
                return;
            }
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, secrets));
            System.out.println("[vault] Loaded " + secrets.size() + " secrets from " + path);
        } catch (Exception e) {
            String msg = "[vault] Failed to load secrets from " + uri + path + " : " + e.getMessage();
            if (failFast) {
                throw new IllegalStateException(msg, e);
            }
            // En dev : log + continue (fallback application*.yml + env vars)
            System.err.println(msg + " — continuing with env/yml defaults (fail-fast=false)");
        }
    }

    /**
     * Appel HTTP brut à l'API Vault KV v2.
     *
     * <p>On évite Spring Vault Core ici car on tourne dans un
     * EnvironmentPostProcessor (avant les beans). On utilise un RestClient
     * standalone qui ne nécessite aucun bean wired.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSecrets(String uri, String token, String path) {
        String fullUrl = uri.replaceAll("/$", "") + "/v1/" + path;
        RestClient client = RestClient.builder().build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", token);

        RequestEntity<Void> req = RequestEntity.method(HttpMethod.GET, URI.create(fullUrl))
            .header("X-Vault-Token", token)
            .build();

        ResponseEntity<Map> response = client.method(HttpMethod.GET)
            .uri(URI.create(fullUrl))
            .header("X-Vault-Token", token)
            .retrieve()
            .toEntity(Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return Map.of();
        }

        // KV v2 wraps payload : { "data": { "data": { actual secrets }, "metadata": {...} } }
        Map<String, Object> body = response.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) return Map.of();
        Map<String, Object> secrets = (Map<String, Object>) data.get("data");
        if (secrets == null) return Map.of();

        // Spring property keys : on garde les keys flat (db.username, jwt.secret, etc.)
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : secrets.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
