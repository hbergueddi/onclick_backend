# Intégration HashiCorp Vault + Redis — Sprint I.8

> Tag : `monolith-v3.11-vault-redis`
> Date : 13 mai 2026

---

## 🔑 HashiCorp Vault

### Architecture

- **Conteneur Docker** : `hashicorp/vault:1.17` en mode dev
- **Port hôte** : `8200` (UI + API)
- **Root token dev** : `oneclick-dev-root` (⚠️ JAMAIS en prod)
- **Storage** : in-memory (dev) → en prod : Consul ou Raft + auto-unseal AWS/GCP KMS
- **Secret engine** : KV v2 sur `/secret/*`

### Démarrage

```bash
# Démarre Vault + bootstrap des secrets
docker compose up -d vault
docker compose run --rm vault-init   # one-shot seeding

# UI : http://localhost:8200/ui  (login → Token → oneclick-dev-root)
# CLI :
docker exec -it oneclick_vault vault kv get secret/oneclick
```

### Structure des secrets

Tous les secrets vivent sous **un seul path** `secret/oneclick` avec des
clés préfixées hiérarchiques. Choix senior : un seul `kv put` simplifie
le bootstrap et permet à `Spring EnvironmentPostProcessor` de tout charger
en un seul GET HTTP (un round-trip réseau, idempotent).

| Préfixe Vault | Clés | Usage Spring |
|---------------|------|--------------|
| `db.*` | username, password, url | `spring.datasource.*` |
| `jwt.*` | secret, issuer, ttl-seconds, refresh-ttl-days | `app.security.jwt-secret` / `jwt-issuer` |
| `ai.groq.*` | api-key, endpoint, model | `app.ai.groq.*` (`GroqClient`) |
| `notification.fcm.*` | project-id, service-account-json | `app.notification.fcm.*` (`FcmClient`) |
| `ocr.space.*` | api-key, endpoint | `app.ocr.space.*` (`OcrService`) |
| `email.resend.api-key` | + `email.from.default` | `app.email.resend.*` (`ResendClient`) |
| `google.places.api-key` | | `app.google.places.api-key` (`GooglePlacesEnrichmentService`) |
| `monitoring.sentry.*` | dsn, environment, traces-sample-rate | (mappé manuellement — pas via auto-config Sentry) |
| `wallet.apple.*` / `wallet.google.*` | pass-type-id, team-id, cert-base64, issuer-id, service-account-json | `app.wallet.*` (`WalletPassService`) |
| `rate-limit.*` | enabled, global-tokens-per-minute, login-tokens-per-minute | `app.rate-limit.*` (`TokenBucket`) |

### Mécanisme d'injection (Spring Boot 4 compatible)

**Pas de Spring Cloud Vault** : Spring Cloud 4.2 ne supporte pas encore Spring
Boot 4.0 (l'interface `WebServerInitializedEvent` a été déplacée). À la place,
on utilise un **`EnvironmentPostProcessor` maison** :

```
com.onesley.oneclick.security.vault.VaultSecretsEnvironmentPostProcessor
```

Enregistré dans `META-INF/spring.factories` sous la nouvelle clé Boot 4 :
```
org.springframework.boot.EnvironmentPostProcessor=\
  com.onesley.oneclick.security.vault.VaultSecretsEnvironmentPostProcessor
```

Au démarrage :
1. Vérifie si le profile `vault` est actif (sinon skip)
2. Lit `VAULT_URI`, `VAULT_TOKEN`, `VAULT_PATH` depuis l'env
3. GET `/v1/{path}` avec header `X-Vault-Token`
4. Parse le wrapping KV v2 (`{data:{data:{<secrets>}}}`)
5. Injecte les keys flat (`db.username`, `jwt.secret`, etc.) dans un
   `MapPropertySource` **en tête de chaîne** (avant application*.yml)
6. Logs `[vault] Loaded N secrets from <path>`

Fail-safe : si Vault est down ou la clé absente, log + continue avec les
defaults de `application*.yml` ou des env vars. En prod, passer
`VAULT_FAIL_FAST=true` pour bloquer le boot.

### Activation côté Spring

```bash
# Dev local
SPRING_PROFILES_ACTIVE=dev,vault \
VAULT_URI=http://localhost:8200 \
VAULT_TOKEN=oneclick-dev-root \
./mvnw spring-boot:run
```

```bash
# Prod (AppRole auth recommandé)
SPRING_PROFILES_ACTIVE=prod,vault \
VAULT_URI=https://vault.oneclick.prod:8200 \
VAULT_TOKEN=$(vault write -field=token auth/approle/login \
  role_id=$ROLE_ID secret_id=$SECRET_ID) \
VAULT_FAIL_FAST=true \
java -jar oneclick.jar
```

### Vérifier en live

```bash
# Lire un secret
docker exec oneclick_vault vault kv get secret/oneclick
docker exec oneclick_vault vault kv get -field=jwt.secret secret/oneclick

# Update un secret (rotation)
docker exec oneclick_vault vault kv patch secret/oneclick \
  ai.groq.api-key="gsk_new_key_xxx"

# Restart Spring pour recharger les secrets (no live reload en dev)
```

---

## ⚡ Redis

### Architecture

- **Conteneur Docker** : `redis:7-alpine`
- **Port hôte** : `6379`
- **Persistence** : appendonly via volume `oneclick_redisdata`
- **Healthcheck** : `redis-cli ping`

### Utilisation Spring

`@Configuration` : `com.onesley.oneclick.cache.CacheConfig`

5 caches typés avec TTL différenciés :

| Cache name | TTL | Usage |
|------------|-----|-------|
| `users-by-email` | 5 min | Lookup auth `UserService.findByEmail()` |
| `tenants-by-slug` | 30 min | Multi-tenant routing `TenantService.findBySlug()` |
| `restaurants` | 10 min | Fiche détail (lecture fréquente) |
| `loyalty-tiers` | 1 h | Table de référence |
| `feature-flags` | 5 min | Toggles `ConfigurationService.featureFlag()` |

Sérialisation : Jackson JSON + JavaTimeModule (debug facile via `redis-cli`).

### Vérifier en live

```bash
# DBSIZE
docker exec oneclick_redis redis-cli DBSIZE

# Lister les clés cachées
docker exec oneclick_redis redis-cli KEYS '*'

# Monitor live (toutes les requêtes Redis)
docker exec oneclick_redis redis-cli MONITOR

# Flush tout (dev only)
docker exec oneclick_redis redis-cli FLUSHALL
```

### Future : sessions + rate-limit distribués

Le TokenBucket actuel (`security/ratelimit/TokenBucket`) est in-memory.
Pour clusteriser : passer à un Redis-backed bucket via `RedisAtomicLong`
ou `RedisTemplate.opsForValue().increment()`. Effort estimé : 0.5 j.

---

## 🚀 Compose

```yaml
# Services
oneclick_postgres       # Postgres 17-alpine
oneclick_redis          # Redis 7-alpine
oneclick_elasticsearch  # ES 9.0.0
oneclick_vault          # HashiCorp Vault 1.17 (dev mode)
oneclick_vault_init     # Bootstrap one-shot des secrets

# Tools (--profile tools)
oneclick_adminer        # UI Postgres (port 8082)
oneclick_kibana         # UI ES (port 5601)
```

### Commandes utiles

```bash
# Démarrage stack complète
docker compose up -d

# Re-seed secrets Vault
docker compose run --rm vault-init

# Logs Vault
docker logs -f oneclick_vault

# Stop tout (data preservée)
docker compose down

# Stop + DROP TOUTES les data
docker compose down -v
```

---

## 🎯 Migration prod (TODO)

| Item | Effort | Notes |
|------|--------|-------|
| Vault prod : storage Consul ou Raft | 0.5 j | Pas de in-memory en prod |
| Vault prod : auto-unseal AWS KMS / GCP KMS | 0.5 j | Sécurité critique |
| Auth method AppRole (rotation programmatique) | 0.5 j | Remplace le root token |
| Policies fines `path "secret/data/oneclick"` | 0.5 j | RBAC Vault |
| Audit logs Vault → SIEM | 0.5 j | Compliance |
| Redis prod : Redis Sentinel ou Redis Cluster | 1 j | HA + sharding |
| TLS Redis + Vault entre Spring et conteneurs | 0.5 j | `xpack` style |
| TokenBucket Redis-backed (clustering) | 0.5 j | Cf section Redis ci-dessus |

**Total prod-hardening Vault + Redis** : ~4-5 jours-dev. Reste lié au déploiement
général (Sprint J — en attente validation dev senior).
