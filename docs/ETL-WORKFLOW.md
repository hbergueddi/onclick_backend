# ETL Workflow — Phase 13 (Sprint G.7)

> Migration des données legacy Supabase Cloud → DB enterprise `oneclick_enterprise`.
>
> **État actuel** : EtlOrchestrator opérationnel · 8 steps (7 Phase A + 1 Phase B) ·
> verify counts post-ETL · dry-run mode pour valider avant run réel.

## Architecture

```
┌─────────────────────┐    postgres_fdw    ┌──────────────────────────┐
│  oneclick_local     │ ─────────────────► │  oneclick_enterprise     │
│  (legacy 95 tables) │   foreign tables   │  (greenfield 60+ tables) │
│  - profiles         │                    │  - users                 │
│  - restaurants      │                    │  - restaurants           │
│  - reservations     │                    │  - reservations          │
│  - loyalty_points   │                    │  - loyalty_accounts +    │
│  - scanned_tickets  │                    │    loyalty_transactions  │
│  - friendships      │                    │  - friendships           │
│  ...                │                    │  ...                     │
└─────────────────────┘                    └──────────────────────────┘
```

**Stratégie** :
- Spring Boot CommandLineRunner sous profile `etl` (web-application-type: none)
- `EtlPostgresFdwBootstrap` setup FDW au boot (CREATE SERVER + IMPORT FOREIGN SCHEMA)
- 7 EtlSteps Phase A (FK order) + 1 PhaseBStep (notifications, social, audit)
- `TRUNCATE` cibles puis `INSERT...SELECT` cross-DB
- Cache Redis flush post-ETL (bypass @CacheEvict)

## Setup pré-requis

### 1. Restaurer le dump Supabase Cloud dans `oneclick_local`

```bash
# Récupérer le dump prod Supabase
supabase db dump --linked --schema public,auth,storage > /tmp/oneclick_prod.sql

# Restaurer dans le container Docker local
docker exec -i oneclick_postgres psql -U hh -d oneclick_local < /tmp/oneclick_prod.sql

# Vérifier
docker exec oneclick_postgres psql -U hh -d oneclick_local -c "\dt"
```

### 2. Vérifier que `oneclick_enterprise` existe avec les migrations Flyway

```bash
# Le compose.yaml crée déjà oneclick_enterprise au premier up
docker exec oneclick_postgres psql -U hh -l | grep oneclick_enterprise

# Migrations Flyway s'exécutent au démarrage Spring avec profile enterprise
SPRING_PROFILES_ACTIVE=enterprise ./mvnw spring-boot:run
# Attendre logs "Flyway: V19 success=true" puis stop
```

## Workflows

### Mode dry-run (valider avant le vrai run)

Avant un ETL réel, vérifier que :
1. La connexion FDW fonctionne
2. Les counts source legacy sont cohérents
3. Aucune table source manque

```bash
ONECLICK_ETL_DRY_RUN=true \
SPRING_PROFILES_ACTIVE=enterprise,etl \
./mvnw spring-boot:run
```

Sortie attendue :
```
 Phase 13 ETL — démarrage (DRY-RUN — pas d'INSERT)
 Setup postgres_fdw : 25 foreign tables importées
 ──────── Dry-run — Verify counts source vs target ────────
  ✓ tenants : source=4, target=0
  ✓ users : source=17221, target=0
  ✓ restaurants : source=1042, target=0
  ✓ reservations : source=503, target=0
  ⚠ scanned_tickets : source=198 (warn — target=0 pour dry-run)
  ...
 Dry-run terminé en 2 s — aucun INSERT effectué
```

Si tout est vert/jaune → on peut lancer le vrai ETL.

### Mode ETL réel

```bash
SPRING_PROFILES_ACTIVE=enterprise,etl \
./mvnw spring-boot:run
```

Sortie attendue (run typique ~1-2s, 50K+ rows) :
```
 Phase 13 ETL — démarrage
 Setup postgres_fdw : 25 foreign tables importées
 TRUNCATE : 30 tables cleared

 ──────── Phase 13.A — Critical path ────────
  tenants            : 4 rows
  roles              : 12 rows
  users              : 17221 rows
  restaurants        : 1042 rows
  restaurant_staffs  : 15449 rows
  reservations       : 503 rows
  loyalty_accounts   : 219 rows
  loyalty_txs        : 397 rows
  offers             : 61 rows
  contracts          : 22 rows

 ──────── Phase 13.B — Secondaire ────────
  notifications      : 1795 rows
  device_tokens      : 47 rows
  friendships        : 145 rows
  referrals          : 11 rows
  support_tickets    : 16 rows
  audit_logs         : 428 rows

 ──────── Flush des caches Redis ────────
  ✓ 12 caches flushed

 ──────── Verify counts (Sprint G.7) ────────
  ✓ tenants : source=4, target=4
  ✓ users : source=17221, target=17221
  ✓ restaurants : source=1042, target=1042
  ...
 Verify rapport : 16 ✓ · 0 ⚠ · 0 ✗

 Phase 13 ETL — terminé en 1.8 s
   TOTAL : 51 577 rows insérées
```

## Configuration

`application-etl.yml` :
```yaml
oneclick:
  etl:
    legacy:
      host: localhost
      port: 5432
      database: oneclick_local
      user: oneclick_app
      password: ${ETL_LEGACY_PASSWORD:OneclickLocal2026}
    fallback-password: ${ETL_FALLBACK_PASSWORD:TestLocal2026!}
    truncate-before-etl: true        # TRUNCATE cibles avant INSERT (idempotent)
    run-phase-a: true                # Critical path
    run-phase-b: true                # Secondary tables
    dry-run: ${ONECLICK_ETL_DRY_RUN:false}     # Sprint G.7
    verify-counts-after-etl: true     # Sprint G.7
```

Variables d'environnement (cf `.env.example`) :
- `ETL_LEGACY_PASSWORD` : password DB legacy (default OneclickLocal2026)
- `ETL_FALLBACK_PASSWORD` : password fallback users sans hash (default TestLocal2026!)
- `ETL_DB_USERNAME` / `ETL_DB_PASSWORD` : user superuser DB cible (hh par défaut)
- `ONECLICK_ETL_DRY_RUN` : `true` pour dry-run mode

## Mappings spéciaux (cf `docs/PHASE13-ETL-MAPPING.md`)

Quelques transformations non-triviales :

| Legacy | Enterprise | Note |
|--------|-----------|------|
| `reservations.date + heure` | `reservations.reservation_at` (timestamptz) | concat + parse |
| `loyalty_points` rows (1 par mouvement) | `loyalty_accounts.balance` + N `loyalty_transactions` | aggregate par (client, resto) |
| `scanned_tickets` | `loyalty_transactions.type='earn'` | filtre + transform |
| `bookable_resources` + `resource_bookings` + `loyalty_punch_cards` | `resources` + `resource_bookings` | merge 3-tables |
| `user_roles` many-to-many | `users.role_id` (1 rôle) | pick highest priority |
| `restaurant_staff` (legacy) | `restaurant_staffs` (enterprise) | rename only |
| `auth.users.encrypted_password` | `users.password_hash` (bcrypt) | direct copy OK |

## Verify counts — comportement

`EtlVerifyService` compare 16 mappings clés (cf liste dans le code) :
- **OK** : counts identiques
- **WARN** : delta ≤ 5% (ex: dump partiel, ou un row corrompu skipped)
- **MISMATCH** : delta > 5% — investiguer

Stratégie : non-bloquant. Le rapport est dans les logs, tu juges si critique.

## Cas d'urgence : re-run l'ETL

Si l'ETL a planté ou si tu veux re-importer les données récentes :

```bash
# 1. Dump frais depuis Supabase Cloud
supabase db dump --linked --schema public,auth > /tmp/oneclick_prod.sql
docker exec -i oneclick_postgres psql -U hh -d oneclick_local -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker exec -i oneclick_postgres psql -U hh -d oneclick_local < /tmp/oneclick_prod.sql

# 2. ETL avec TRUNCATE (idempotent)
SPRING_PROFILES_ACTIVE=enterprise,etl ./mvnw spring-boot:run
```

## Limitations connues (V2)

1. **Pas de migration incrémentale** : ETL = TRUNCATE+INSERT full. Pour V2,
   ajouter un mode `--since=2026-05-01` qui ne migre que les rows modifiés.

2. **Pas de rollback automatique** : si étape 5/7 échoue, les 4 premières sont
   déjà committées. Workaround : `truncate-before-etl=true` au re-run.

3. **Cache Redis flushé en bloc** : OK pour V1 (1-2 min downtime acceptable).
   V2 : invalidation key par key pour zero-downtime.

4. **Validation row-level** : on compte mais on ne valide pas les FK. Si une
   row source pointe vers une FK qui n'a pas été migrée, INSERT silently skip.
   V2 : log explicit + report.

## Tags

| Tag | Description |
|-----|-------------|
| `monolith-v3.2-etl-ready` | ETL polish : dry-run mode + verify counts + doc workflow |
