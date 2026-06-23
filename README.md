# OneClick — Backend (Spring Boot)

Monolithe modulaire (**Spring Modulith**) qui sert l'API REST + WebSocket de la
plateforme OneClick : fidélité, réservations, multi-tenant whitelabel (PCC, HOMU…),
RBAC fin (`hasAuthority('VERB:RESOURCE')`).

- **API** : `http://localhost:8083` · **Swagger** : `http://localhost:8083/swagger-ui.html`
- **Stack** : Spring Boot · Spring Modulith · PostgreSQL 17 · Redis · Flyway · OAuth2 Resource Server (JWT HS256)
- **Profil par défaut** : `enterprise` (DB `oneclick_enterprise`, port 8083). `secure` ajoute l'auth JWT.

## Démarrage rapide (clone → run)

> Procédure complète et faisant foi (Docker **ou** Homebrew, restauration de la base,
> création du user applicatif, étape `BYPASSRLS`) : **[`SETUP-LOCAL.md`](SETUP-LOCAL.md)**.

```bash
# 1. Postgres 17 + Redis 7 en local (Docker) — + Adminer sur http://localhost:8082
docker compose up -d

# 2. Base + user applicatif : suivre SETUP-LOCAL.md

# 3. Lancer le backend
./run.sh                 # profil enterprise (port 8083)
./run.sh --secure        # + OAuth2 JWT
```

**Avec IntelliJ** : `File > Open` sur le `pom.xml` (projet Maven), puis une run config
sur la classe `OneclickApplication` avec `--spring.profiles.active=enterprise` (port 8083).

## Build & tests

```bash
./mvnw clean package     # build le JAR → target/oneclick-0.0.1-SNAPSHOT.jar
./mvnw test              # suite unitaire + intégration
```

## Configuration

Tous les secrets sont externalisés via variables d'environnement ; en dev local des
valeurs par défaut suffisent (rien à configurer pour démarrer). Voir
`application-enterprise.yml`, et pour la prod `deploy/oneclick-prod.env.template` +
`deploy/DEPLOYMENT-ENV.md`. Le runbook de déploiement VPS est dans
`../OneClick_iOS_Native/docs/ENVIRONMENTS-WORKFLOW.md`.

## Frontend

Le frontend React (portails client / **OneClick Business** / super-admin) vit dans le repo
[`OneClick_Spring_FrontEnd`](https://github.com/hiham/OneClick_Spring_FrontEnd) et consomme cette API.
