# Infra HashiCorp Vault + Redis — Sprint I.8

> Date : 13 mai 2026
> Scope : **install + config uniquement**. L'intégration côté application
> (comment Spring/le frontend consomment les secrets) est à décider avec
> le dev senior dans un sprint dédié.

---

## ⚡ Redis (déjà en place avant Sprint I.8)

- **Container Docker** : `redis:7-alpine`
- **Port hôte** : `6379`
- **Persistence** : appendonly via volume `oneclick_redisdata`
- **Healthcheck** : `redis-cli ping`

### Vérifier en live

```bash
docker exec oneclick_redis redis-cli ping       # → PONG
docker exec oneclick_redis redis-cli DBSIZE     # nombre de clés
docker exec oneclick_redis redis-cli KEYS '*'   # lister tout
docker exec oneclick_redis redis-cli MONITOR    # stream temps réel
docker exec oneclick_redis redis-cli FLUSHALL   # purge dev only
```

---

## 🔐 HashiCorp Vault (nouveau Sprint I.8)

### Architecture infra

- **Conteneur Docker** : `hashicorp/vault:1.17` en mode dev
- **Port hôte** : `8200` (UI + API)
- **Root token dev** : `oneclick-dev-root` (⚠️ JAMAIS en prod)
- **Storage** : in-memory (dev) — en prod : Consul ou Raft + auto-unseal AWS/GCP KMS
- **Secret engine** : KV v2 sur `/secret/*`

### Démarrage

```bash
# Démarre Vault + bootstrap des secrets (idempotent)
docker compose up -d vault
docker compose run --rm vault-init

# UI : http://localhost:8200/ui  (login → Token → oneclick-dev-root)
```

### Structure des secrets seedés

Tous les secrets vivent sous **un seul path** `secret/oneclick` avec des
clés préfixées hiérarchiques. Un seul `kv put` simplifie le bootstrap et
permet à un consommateur (Spring, Vault Agent, sidecar, etc.) de tout
récupérer en un seul appel KV v2.

| Préfixe | Clés |
|---------|------|
| `db.*` | username, password, url |
| `jwt.*` | secret, issuer, ttl-seconds, refresh-ttl-days |
| `ai.groq.*` | api-key, endpoint, model |
| `notification.fcm.*` | project-id, service-account-json |
| `ocr.space.*` | api-key, endpoint |
| `email.*` | resend.api-key, from.default |
| `google.places.api-key` | clé Google Places enrichissement restos |
| `monitoring.sentry.*` | dsn, environment, traces-sample-rate |
| `wallet.apple.*` | pass-type-id, team-id, cert-base64 |
| `wallet.google.*` | issuer-id, service-account-json |
| `rate-limit.*` | enabled, global-tokens-per-minute, login-tokens-per-minute |

→ **28 keys au total** dans `secret/oneclick`.

### Vérifier / Lire / Update en live

```bash
# Lister
docker exec oneclick_vault vault kv list secret/
docker exec oneclick_vault vault kv get secret/oneclick

# Lire un champ précis
docker exec oneclick_vault vault kv get -field=jwt.secret secret/oneclick
docker exec oneclick_vault vault kv get -field=ai.groq.api-key secret/oneclick

# Update (rotation)
docker exec oneclick_vault vault kv patch secret/oneclick \
  ai.groq.api-key="gsk_new_xxx"

# Métadonnées (versions)
docker exec oneclick_vault vault kv metadata get secret/oneclick
```

---

## 🚀 Stack Docker Compose

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
docker compose up -d                     # Stack complète
docker compose run --rm vault-init       # Re-seed secrets Vault
docker logs -f oneclick_vault            # Logs Vault
docker compose down                      # Stop (data préservée)
docker compose down -v                   # Stop + DROP TOUTES les data
```

---

## ⏭ Reste à décider (avec le dev senior)

Le container Vault tourne et contient les 28 secrets. Reste à choisir
**comment l'application va les consommer** :

| Option | Effort | Note |
|--------|--------|------|
| Spring lit Vault via `EnvironmentPostProcessor` maison | 0.5 j | Compat Spring Boot 4, refresh manuel |
| Spring Cloud Vault Config | — | ❌ Pas compat Spring Boot 4 actuellement |
| Vault Agent + template fichier monté | 0.5 j | Découple app de Vault, refresh auto |
| Sidecar `consul-template` (K8s) | 1 j | Pattern courant en prod cloud-native |
| Env vars injectées par le pipeline déploiement | 0.2 j | Simple, mais moins sécurisé (vars en clair) |
| Vault CSI Provider (Kubernetes) | 1 j | Si déploiement K8s, montre les secrets en fichiers |

Cette décision attend la validation du dev senior + le choix du mode de
déploiement (VPS bare-metal, Docker Swarm, Kubernetes, etc.).

---

## 🟡 Reste à faire pour la prod (Vault + Redis)

| Item | Effort |
|------|--------|
| Vault prod : storage Consul ou Raft (au lieu de in-memory) | 0.5 j |
| Vault prod : auto-unseal AWS KMS / GCP KMS | 0.5 j |
| Vault prod : auth method AppRole (rotation programmatique) | 0.5 j |
| Vault prod : policies fines `path "secret/data/oneclick"` | 0.5 j |
| Vault prod : audit logs → SIEM | 0.5 j |
| Redis prod : Redis Sentinel ou Redis Cluster (HA + sharding) | 1 j |
| TLS Spring ↔ Redis et Spring ↔ Vault | 0.5 j |

**Total prod-hardening** : ~4 j (intégré au Sprint J déploiement, en attente
validation dev senior).
