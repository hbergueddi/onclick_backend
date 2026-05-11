# Phase 13 — ETL `oneclick_local` → `oneclick_enterprise`

> Mapping des données legacy Supabase-style (95 tables) vers la nouvelle architecture enterprise (67 tables).

## Vue d'ensemble

| | Legacy | Enterprise |
|---|---|---|
| Tables totales | 95 | 67 |
| Tables avec data | 67 | 0 (greenfield) |
| Volume principal | 17k users, 1k restos, 3.5k résas, 40k notifs, 17k staff | — |

---

## Mapping par priorité

### Niveau A — Critical path (Phase 13.A)

| # | Enterprise (cible) | Legacy (source) | Volume | Transformation |
|---|---|---|---|---|
| 1 | `tenants` | `tenants` | 5 → 5 | Direct (preserve UUIDs). Drop columns `features` (jsonb), `legal_name` |
| 2 | `tenant_brandings` | `tenant_branding` (singular) | 5 → 5 | Rename + preserve UUID |
| 3 | `tenant_features` | `tenant_features` | 47 → 47 | Direct |
| 4 | `company_settings` | `company_settings` | 1 → 1 | Direct |
| 5 | `roles` | hardcoded enum + `user_roles.role` distinct | — → 5 | Build canonical roles from enum: `CLIENT`, `RESTAURATEUR`, `STAFF`, `GROUP_ADMIN`, `SUPERADMIN` |
| 6 | `users` | `profiles` JOIN `user_roles` (priority pick) | 17221 → 17221 | Fusion. Role canonicalisé (1 user = 1 role). password_hash = BCrypt à générer (cf décision #2) |
| 7 | `restaurants` | `restaurants` | 1042 → 1042 | Drop columns Google `*` + `search_vector` + `referral_*` + tags. Preserve UUIDs. |
| 8 | `restaurant_staffs` (plural !) | `restaurant_staff` (singular) | 16577 → 16577 | Rename. `staff_role` enum → `role_code` text |
| 9 | `restaurant_zones` | `restaurant_zones` | ? → ? | Direct |
| 10 | `restaurant_tables` | `restaurant_tables` | ? → ? | Direct |
| 11 | `restaurant_services` | `restaurant_services` (renamed entity `MealService`) | 3141 → 3141 | Direct |
| 12 | `business_hours` | `restaurants.opening_hours` (jsonb) | 1042 → ~7000 | Explode jsonb → 7 rows per resto (day_of_week 0-6) |
| 13 | `reservations` | `reservations` | 3552 → 3552 | Drop columns proposed_*, reminder_*, no_show_*, late_cancellation (workflow PCC). Map `date+heure` text → `reservation_at` timestamptz |
| 14 | `reservation_guests` | `reservation_guests` | 194 → 194 | Direct |
| 15 | `booking_rules` | `booking_rules` | 7294 → 7294 | Direct |
| 16 | `loyalty_accounts` | GROUP BY `loyalty_points` (client_id, restaurant_id) | — → ~1500 | **Refonte** : 1 account par (client × resto), balance = sum(points) |
| 17 | `loyalty_transactions` | `loyalty_points` (1 row = 1 earn/spend event) | 1497 → 1497 | 1 row = 1 transaction type=`earn` |
| 18 | `redemptions` | `redemption_events` | 10 → 10 | Direct + map au loyalty_account |
| 19 | `tiers` | `tier_thresholds` + `restaurant_tier_config` | 4 → 4 | Direct |
| 20 | `loyalty_rules` | `gain_rules` + `restaurant_gain_rules` | 9+1030 → 1030 | Per-resto avec fallback global |
| 21 | `offers` | `offers` | 84 → 84 | Direct. Drop `tier_*` columns si présent |
| 22 | `contracts` | `partner_contracts` | 31 → 31 | Beaucoup de columns legacy (raison_sociale, ice, rib, jours_fermeture, etc.) — drop ou push dans jsonb metadata |
| 23 | `invoices` | `oneclick_hi_invoices` | 25 → 25 | Direct + rename |
| 24 | `invoice_lines` | `invoice_lines` | 27 → 27 | Direct |
| 25 | `wallet_transactions` | `admin_wallet_transactions` | 50 → 50 | Direct + rename |

**Total Phase 13.A** : ~37k rows insérés (essentiellement users + staff).

---

### Niveau B — Secondaire (Phase 13.B)

| # | Enterprise | Legacy | Volume | Transformation |
|---|---|---|---|---|
| 26 | `notifications` | `notifications` | 40019 → 40019 | Direct. Drop `metadata.action_url` mappé vers `link` |
| 27 | `device_tokens` | `device_tokens` | 109 → 109 | Direct |
| 28 | `friendships` | `friendships` | 1300 → 1300 | Direct. Swap user1/user2 si nécessaire (canonical < check constraint) |
| 29 | `referrals` | `referrals` | 12 → 12 | Direct |
| 30 | `support_tickets` | `support_tickets` | 4 → 4 | Direct |
| 31 | `ticket_messages` | `chat_messages` ? | ? | À vérifier — sinon vide |
| 32 | `events` | `tenant_events` | 18 → 18 | Direct + rename |
| 33 | `event_participations` | `event_rsvps` | 2 → 2 | Direct + rename |
| 34 | `resources` | `bookable_resources` | 33 → 33 | Direct + rename |
| 35 | `resource_bookings` | `resource_bookings` | 76 → 76 | Direct |
| 36 | `audit_logs` | `action_logs` + `admin_audit_log` | 3875 → 3875 | Concatener les 2 sources |
| 37 | `error_logs` | `monitor_logs` filtré (severity=error) | 4734 → ~500 | Filter |
| 38 | `notification_campaigns` | `promo_notification_requests` | ? | Mapping |

**Total Phase 13.B** : ~50k rows.

---

### Niveau C — Sans équivalent direct (Phase 13.C → SKIP)

Tables qui ne mappent pas vers le schéma enterprise. Plusieurs catégories :

**1. Concepts pas dans la spec senior dev** :
- `client_ratings` (194) — score de fiabilité client (mais profile.reliability_score est dans `users`)
- `user_favorites` (21) — pas dans spec
- `loyalty_punch_cards` (7) — punch cards PCC (Phase 2 / whitelabel)
- `point_distributions`, `point_gifts`, `loyalty_plafonds` — features loyalty non-spec
- `tier_restaurant_offers`, `restaurant_tier_status` — features tier non-spec
- `expired_points`, `restaurant_expired_pool`, `restaurant_restitutions` — workflow expiration

**2. Tables techniques internes** (logs / monitoring) :
- `monitor_logs` (4734) — sauf si on filtre comme error_logs
- `system_alerts`, `system_alert_rules`, `system_health_checks` — monitoring stack
- `ai_usage`, `ai_usage_bypass` — rate limit IA (à refaire côté enterprise différemment)
- `fraud_alerts`, `no_show_disputes` — workflow no-show
- `lifecycle_events` — log de transitions

**3. Features whitelabel / tenant-specific** :
- `pcc_family_members` (2), `pcc_feedbacks` (6) — feature PCC
- `elite_applications`, `elite_events`, `elite_rsvps` — feature Elite
- `seminar_requests` (1) — feature séminaires
- `tenant_announcements` (12), `announcement_reads` (15) — annonces tenant
- `staff_notification_preferences` — préférences staff

**4. Documents / contrats avancés** :
- `app_documents`, `document_versions` — gestion docs
- `contract_disabled_articles`, `contract_history`, `contract_template_articles`, `contract_templates`, `rule_templates` — templates de contrats

**5. Permissions complexes** :
- `staff_role_permissions` (260), `app_permissions` (116), `custom_roles` — système RBAC legacy
  → Le système enterprise (`permissions` = `(menu_id, action_id)`) est SIMPLIFIÉ. La reconstruction nécessiterait une refonte conceptuelle.

**6. Workflow inscription** :
- `team_invitations`, `onboarding_requests`, `email_bounces`, `quota_change_logs`, `gain_rule_requests` — workflows admin
- `tenant_admins` (4) — résolu via `users.role_id = SUPERADMIN` enterprise
- `restaurant_groups` (4) — pas modélisé en enterprise (multi-resto = via `tenant_id`)

**7. Tracking** :
- `offer_impressions`, `contact_import_events`, `chat_messages` — événements

**Total skipped** : ~12000 rows ignorés (essentiellement monitoring/audit non-critique).

---

## Décisions à valider avec le user

### 🔑 Décision #1 — Préservation des UUIDs

**Question** : faut-il préserver les UUID legacy lors de l'INSERT enterprise ?

**Recommandation** : **OUI**.
- Les apps mobiles iOS/Android cachent des UUID localement (résas, points, etc.)
- Si on régénère les UUIDs, on casse toutes les références au moment du switch
- Les rares conflits (UUIDs déjà utilisés en enterprise pour tests) → wipe enterprise avant ETL

### 🔑 Décision #2 — Mot de passe des 17k users — VALIDÉ : Option (a+) (amélioration découverte)

**Découverte audit Phase 13.1** : `auth.users` (schéma Supabase managé local) contient les
**vrais BCrypt hashes** au format `$2a$10$...` (60 chars). Inspection :
- 17203 users avec hash `$2a$10$` (cost 10)
- 11 users avec hash `$2a$06$` (cost 6, comptes anciens)
- 7 users sans hash (comptes orphelins / OAuth uniquement)
- Mapping parfait `profiles.id = auth.users.id` (0 orphans)

→ Ces hashes sont **100% compatibles avec Spring `BCryptPasswordEncoder`** (pas de pepper
Supabase, juste bcrypt standard).

**Décision retenue (améliorée)** : **Option (a+) = préserver les hashes Supabase + fallback**
- Pour chaque user avec `auth.users.encrypted_password` non null → réutiliser le hash tel quel
- Pour les 7 users sans hash → BCrypt(`TestLocal2026!`)

**Avantages** :
- Les vrais mdp continuent de marcher (pas de coupure pour les testers)
- Les comptes seed `TestLocal2026!` continuent de fonctionner (ce sont LEURS hashes)
- Pas plus de code que option (a) (juste lire la colonne au lieu de générer)
- Sécurité réelle vs mdp universel

**⚠️ Réserve maintenue pour la prod** : même avec ce schéma, si on migre la prod réelle,
il restera prudent d'ajouter une migration future V8 avec `must_change_password` pour
forcer rotation des hashes anciens (`$2a$06$` cost 6 = obsolète).

**Référence spec** : §20 `Spring Security` — BCrypt conforme. Préserver les hashes existants
est un best practice de migration (pas de force-reset inutile).

### 🔑 Décision #3 — Canonicalisation des rôles

**Problème** : `user_roles` legacy a 1 row par (user, role) — un user peut avoir plusieurs rôles. Enterprise = 1 user = 1 role.

**Vu en data** : la migration session 22 a déjà nettoyé "8510 doublons client+restaurateur". Il reste potentiellement des users avec multiples rôles.

**Stratégie** : priorité (du plus élevé au plus bas) :
1. `super_admin` ou `admin` → `SUPERADMIN`
2. `group_admin` → `GROUP_ADMIN`
3. `owner` ou `restaurant_owner` → `RESTAURATEUR`
4. `staff`, `manager`, `waiter`, `server` → `STAFF`
5. `client` (défaut) → `CLIENT`

À implémenter via une CTE SQL `ranked_roles` qui prend le plus haut rang par user.

### 🔑 Décision #4 — Loyalty : refonte 1497 lignes — VALIDÉ avec correctif

**Spec §6** définit **3 tables distinctes** : `LoyaltyAccount`, `LoyaltyTransaction`, `Redemption`.
Pattern DDD : `LoyaltyTransaction` = ledger comptable (debit/credit du compte),
`Redemption` = événement métier (quoi a été échangé, montant discount).

**Stratégie ETL — pattern ledger + business event** :

1. **`loyalty_accounts`** ← agrégation par (client, resto)
   ```sql
   INSERT INTO loyalty_accounts (id, client_id, restaurant_id, balance)
   SELECT gen_random_uuid(), client_id, restaurant_id, COALESCE(SUM(remaining_points), 0)
   FROM legacy.loyalty_points
   GROUP BY client_id, restaurant_id
   ```

2. **`loyalty_transactions` (type=`earn`)** ← `loyalty_points` legacy (1497 rows)
   - `id` preservé
   - `type` = `'earn'`
   - `points` = `points` legacy
   - `amount` = `amount_ttc` legacy
   - `expires_at` = `expires_at` legacy
   - `account_id` = lookup dans loyalty_accounts par (client_id, restaurant_id)

3. **`redemptions`** ← `redemption_events` legacy (10 rows)
   - 1 row par event : `account_id`, `points_used`, `discount_amount`

4. **`loyalty_transactions` (type=`spend`)** ← `redemption_events` legacy (10 rows)
   - Double-write : pour chaque redemption_event, créer AUSSI une `loyalty_transactions` avec
     `type='spend'`, `points = -points_used`, `amount = -discount_amount`.
   - Pourquoi ? `LoyaltyTransaction` est le ledger comptable : tous les mouvements de balance
     doivent y figurer. `Redemption` est l'événement business avec contexte (quoi a été
     échangé, par qui).

5. **Vérification post-ETL** :
   ```sql
   SELECT a.id, a.balance,
          SUM(t.points) AS computed_balance
   FROM loyalty_accounts a
   LEFT JOIN loyalty_transactions t ON t.account_id = a.id
   GROUP BY a.id, a.balance
   HAVING a.balance != COALESCE(SUM(t.points), 0)
   ```
   Doit retourner **0 rows** (cohérence balance = somme des transactions).

### 🔑 Décision #5 — Niveau d'ETL

Quel(s) niveau(x) on fait dans cette première vague Phase 13 ?

**Recommandation** : **Phase 13.A seule (~37k rows, 25 tables)** d'abord.
- Couvre 95% des cas d'usage du frontend
- Validation possible end-to-end : login (users) → liste restos → réservation → loyalty
- Si OK → on enchaîne 13.B (50k rows additionnels, notif/social/audit)
- Si problème → on isole vite

### 🔑 Décision #6 — Stratégie d'exécution

**Option 1** : Spring Boot CommandLineRunner avec profil `etl`
- ✅ Réutilise les entities JPA, BCrypt, validation Bean Validation
- ✅ Logging Spring Boot standard
- ❌ Plus de code à écrire
- ❌ Lent pour 17k+ rows si on passe par JPA (Hibernate)

**Option 2** : Script SQL pur (`V100__etl_legacy.sql` dans Flyway)
- ✅ Très rapide (INSERT ... SELECT direct entre DBs via `dblink` ou `postgres_fdw`)
- ❌ Pas de BCrypt natif Postgres (extension `pgcrypto` a `crypt()` mais pas BCrypt)
- ❌ Validation Java perdue
- ❌ Difficile d'enchainer logique conditionnelle complexe

**Option 3** : Hybride
- SQL pour les tables triviales (FK simples, pas de transformation) via `postgres_fdw`
- Java/Spring pour les transformations complexes (users avec BCrypt, loyalty_accounts depuis aggregation)

**Recommandation** : **Option 3 (hybride)**.
- ETL Java pour : users (BCrypt), loyalty_accounts (aggregation), business_hours (jsonb→rows), audit_logs (concat 2 sources)
- ETL SQL pour : tenants, restaurants, staff, reservations, offers, contracts, notifications (gros volume direct)

---

## Plan d'exécution

### 13.3 — Implementation (~2-3h)

1. **Spring profile `etl`**
   - Active la datasource secondaire `oneclick_local` (read-only)
   - Désactive le serveur web (CLR-only)
   - `application-etl.yml`

2. **EtlOrchestrator + EtlSteps**
   - `tenant`, `users`, `restaurant`, `loyalty`, ...
   - Chaque step : `clearTarget()` → `extractFromLegacy()` → `transform()` → `loadEnterprise()`
   - Logging : count avant/après + sample IDs

3. **postgres_fdw** comme connecteur cross-DB pour les SQL bulk
   - `CREATE EXTENSION postgres_fdw`
   - `CREATE SERVER legacy FOREIGN DATA WRAPPER postgres_fdw`
   - `CREATE FOREIGN TABLE` pour les tables source

4. **Validation script** post-ETL
   - `SELECT count(*) FROM users` → 17221
   - `SELECT count(*) FROM restaurants` → 1042
   - Sample query : `findByEmail` retourne le profil correctement
   - Login test via API : `POST /api/users/by-email` avec mdp universel

### 13.4 — Validation E2E

- ✅ Boot Spring sur enterprise profile
- ✅ `GET /api/users?page=0&size=10` retourne 10 users avec emails legacy
- ✅ `GET /api/restaurants?city=Casablanca` retourne ~100 restos
- ✅ `GET /api/loyalty/accounts/by-client/{id}` retourne le solde correct
- ✅ Test E2E réservation pour 1 user / 1 resto migré

---

## Tables qui restent SANS ETL après Phase 13

Décisions à reprendre plus tard pour ces tables :
- `client_ratings` → soit ajout column `users.reliability_score` (legacy l'a déjà comme `profiles.reliability_score`), soit table dédiée future
- `loyalty_punch_cards` → Phase 2 ou migration whitelabel
- `pcc_*` → migration tenant whitelabel future
- Permissions legacy → reconstruction `permissions/menus/actions` enterprise (workflow admin)
