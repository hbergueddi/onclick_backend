# Setup local — OneClick Spring backend

> Reproduit l'environnement de dev complet sur une machine neuve. À jour : 06 mai 2026, fin Phase 3.

## Prérequis

| Outil | Version | Install |
|---|---|---|
| **Java** | 26+ | `brew install openjdk` puis `JAVA_HOME=/Users/.../openjdk-26.0.1/Contents/Home` |
| **Maven Wrapper** | inclus | `./mvnw` (pas besoin de Maven global) |
| **PostgreSQL** | 17+ | `brew install postgresql@17 && brew services start postgresql@17` |
| **Redis** | 7+ | `brew install redis && brew services start redis` |

Vérification rapide :
```bash
java -version             # openjdk 26.x
nc -z localhost 5432      # postgres up
nc -z localhost 6379      # redis up
```

## 1. Restaurer la base depuis le dump prod

Le dump prod (~33 MB compressé) est produit par `scripts/backup.sh` (Phase 1) et stocké dans `~/Documents/oneclick-prod-YYYYMMDD-HHMMSS/`. Pour restaurer en local :

```bash
# Crée la DB cible (fresh)
createdb oneclick_local

# Restaure le pg_custom dump
pg_restore -d oneclick_local --no-owner --no-privileges \
  ~/Documents/oneclick-prod-20260506-111655/db-full.dump

# Note : ~261 erreurs "role authenticated/service_role does not exist" sont
# attendues — ce sont des rôles Supabase abandonnés, ignorés par notre stack.
```

Vérification :
```bash
psql -d oneclick_local -c "SELECT count(*) FROM public.restaurants;"   # ~1042
psql -d oneclick_local -c "SELECT count(*) FROM public.reservations;"  # ~3552
```

## 2. Créer le user applicatif

```bash
psql -d oneclick_local <<'EOF'
CREATE USER oneclick_app WITH PASSWORD 'OneclickLocal2026';
GRANT CONNECT ON DATABASE oneclick_local TO oneclick_app;
GRANT USAGE ON SCHEMA public, auth TO oneclick_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO oneclick_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO oneclick_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO oneclick_app;
GRANT SELECT ON ALL TABLES IN SCHEMA auth TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO oneclick_app;
EOF
```

## 3. ⚠️ ALTER ROLE BYPASSRLS (étape critique)

Le dump Supabase préserve `ENABLE ROW LEVEL SECURITY` sur les 93 tables `public.*` mais V1 strippe les policies. Sur une DB legacy restaurée, le user `oneclick_app` voit donc 0 ligne (PG refuse tout aux non-owner sans policy).

**Solution one-shot** : donner à `oneclick_app` le bypass de RLS.

```bash
psql -d oneclick_local -c "ALTER ROLE oneclick_app BYPASSRLS;"
psql -d oneclick_local -c "SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname='oneclick_app';"
# attendu : oneclick_app | t
```

Ce statut survit aux restaurations futures du même user. Pour une DB *fresh* créée par Flyway depuis zéro (CI/CD prod), V1 ne réactive pas RLS donc pas besoin de BYPASSRLS — le pipeline se contente du flow normal.

## 4. Compiler + tester

```bash
cd ~/Documents/Onesley/OneClick_Spring
export JAVA_HOME=/Users/hh/Library/Java/JavaVirtualMachines/openjdk-26.0.1/Contents/Home
./mvnw clean compile
./mvnw spring-boot:run
```

App démarre sur **http://localhost:8081**. Endpoints utiles :

| URL | Usage |
|---|---|
| http://localhost:8081/swagger-ui/index.html | Swagger UI (toutes les API documentées) |
| http://localhost:8081/v3/api-docs | OpenAPI 3 JSON |
| http://localhost:8081/actuator/health | Healthcheck (DB + Redis) |
| http://localhost:8081/actuator/flyway | État des migrations |

Premier démarrage : Flyway baseline V1 (BASELINE_IGNORED sur DB existante, applique sur DB fresh). Voir `flyway_schema_history` :
```bash
psql -d oneclick_local -c "SELECT * FROM flyway_schema_history;"
```

## 5. Tests rapides Phase 3 — pilotes

Pour vérifier que les 7 entités pilotes fonctionnent :

```bash
# user_roles
SAMPLE_USER=$(psql -d oneclick_local -t -A -c "SELECT user_id FROM user_roles LIMIT 1")
curl -s "http://localhost:8081/api/user-roles?userId=$SAMPLE_USER" | python3 -m json.tool

# tenants
curl -s "http://localhost:8081/api/tenants/by-slug/oneclick" | python3 -m json.tool

# restaurants (paginated view)
curl -s "http://localhost:8081/api/restaurants/core/by-city?city=Casablanca&size=3" | python3 -m json.tool

# reservations
curl -s "http://localhost:8081/api/reservations/by-status?status=demand%C3%A9e" | python3 -m json.tool | head -30
```

## Profile prod (pour info)

Pas activé en local. Sur le VPS / Docker :
```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL="jdbc:postgresql://prod-host:5432/oneclick" \
DB_USERNAME=oneclick_app \
DB_PASSWORD=*** \
REDIS_HOST=*** \
REDIS_PASSWORD=*** \
java -jar oneclick-backend.jar
```

Cf `src/main/resources/application-prod.yml` pour la liste exhaustive des env vars.

## Troubleshooting

| Symptôme | Cause | Solution |
|---|---|---|
| `must be owner of table X` au boot | RLS actif sans policy, user pas owner | `ALTER ROLE oneclick_app BYPASSRLS` |
| GET retourne `[]` sur des données qui existent | Idem (RLS refuse silencieusement) | Idem ↑ |
| `Failed to start: could not find class GenericObjectPoolConfig` | commons-pool2 manquant | Déjà dans pom.xml, faire `./mvnw clean install` |
| `BeanCreationException: redisHealthContributor` | Redis down | `brew services start redis` |
| `Failed to validate database schema` | Une entité Java déclare une colonne absente en DB | Soit corriger le mapping, soit créer une migration V2+ qui ajoute la colonne |
| Boot lent (>10s) | DevTools en conflit avec hot reload | `./mvnw spring-boot:run -Dspring-boot.run.fork=false` |
