# PROD-DB-CLEANUP-PLAN.md — Nettoyage base de production `oneclick`

> Document vivant. Mis à jour à chaque phase. Opération **destructive** sur la PROD (`oneclick` @ VPS 51.83.162.100, `localhost:5432`, user `postgres`).
> Démarré le **2026-06-23**. Statut global : **✅ TERMINÉ (2026-06-23)** — voir §10 État final livré.

## 1. Objectif (demande utilisateur)
- **OneClick (clients)** : ne garder que `client1..25` (`clientN@clientN.com`), supprimer tous les autres comptes clients.
- **OneClick Business (restaurants)** : garder **5 restos par ville, les plus chers**, supprimer le reste + **vider** leurs données.
- Les 5/ville gardés : **alimentés en données Google Places** (horaires, GPS, adresse…).
- **PCC (palmeraie)** : **ne pas toucher**. Doit rester **accessible depuis OneClick Business** (Store).

## 2. Décisions verrouillées (validées par l'utilisateur)
1. **Mode = HARD PURGE** (suppression physique, base réellement vidée).
2. **Périmètre = tout sauf PCC** → purge OneClick (cull 5/ville) + lagrillardiere + restopro + **HOMU** + tenants de test (l4-*/x). **Seul `palmeraie` est intact.**
3. **Classement « 5 plus chers / ville » = OPTION PRIX** : palier budget `€€€€>€€€>€€>€>(NULL)`, départage `google_rating DESC`, puis `google_reviews_count DESC`, puis `name ASC`.
4. **Pas de ré-enrichissement des 1010** (déjà enrichis à 1003/1010) — on enrichit seulement les **7 sans place_id** (avant classement) + un **`force` ciblé sur les 50 gardés** (fin). Coût Google ≈ 0–2 $.

### Hypothèses de sécurité appliquées
- **Gardés d'office** : 1 SUPERADMIN + 3 GROUP_ADMIN (sinon plus d'accès admin).
- **HOMU confirmé supprimé** (tenant vidé).
- **Schéma Supabase résiduel** (`auth.*`, `user_roles`) : **non touché** (Spring lit `public.*` via `role_id`).

## 3. État réel constaté (2026-06-23, avant purge)
- Users actifs : **17 304** (RESTAURATEUR 17 051 · CLIENT 249 · GROUP_ADMIN 3 · SUPERADMIN 1). Total lignes users (incl. soft-deleted) ~18 115.
- Restaurants actifs : oneclick **1010** · lagrillardiere 17 · **palmeraie 10** · restopro 5 · homu 1.
- OneClick : 10 villes ~100 chacune. `budget` NULL à 64 % (€€€€=5, €€€=53, €€=213, €=90, NULL=649). 1003/1010 ont déjà `google_place_id`.
- PCC (palmeraie) : 10 restos, **221 resources**, 28 memberships actifs, 16 staff.

## 4. Définition keep-set / delete-set

### keep_restaurants
- **50** = top-5/ville parmi `restaurants` tenant=oneclick `deleted_at IS NULL`, classement option-prix (cf §2.3), `row_number() OVER (PARTITION BY city ORDER BY <prix>) <= 5`.
- **+ TOUS** les restos `tenant = palmeraie` (toutes lignes, ne pas toucher).

### keep_users (UNION)
- `clientN@clientN.com` pour N ∈ [1..25] (liste explicite).
- role = SUPERADMIN, role = GROUP_ADMIN.
- staff des keep_restaurants : `restaurant_staffs.user_id` où `restaurant_id ∈ keep_restaurants`.
- **Tout utilisateur lié à PCC** (filet large anti-corruption) : `users.tenant_id = palmeraie` ∪ `restaurant_staffs(user)` des restos palmeraie ∪ `tenant_memberships(user)` palmeraie ∪ `pcc_family_members(member_id, related_member_id)` ∪ `pcc_feedbacks(member_id, reply_by)` ∪ `pcc_story_views(user_id)` ∪ `pcc_stories(author_id)` ∪ `resource_bookings(organizer_id)` ∪ `resource_booking_guests(guest_user_id)` ∪ `member_posts(author_id)`.

### delete-set
- `delete_restaurants` = `restaurants` où `id NOT IN keep_restaurants` ET `tenant != palmeraie` (inclut soft-deleted non-palmeraie).
- `delete_users` = `users` où `id NOT IN keep_users`.

## 5. Graphe FK (analysé) — ordre de purge
- Majorité des enfants : **CASCADE** (purge auto).
- **RESTRICT → users** (à purger d'abord pour le delete-set) : `payments.user_id`, `reservations.client_id`, `resource_bookings.organizer_id`, `support_tickets.opened_by`.
- **RESTRICT → restaurants** : `contracts.restaurant_id`, `invoices.restaurant_id`, `reservations.restaurant_id`, `wallet_transactions.restaurant_id`.
- `created_by/updated_by` = **SET NULL** (zéro impact lignes gardées).
- **Aucun trigger DELETE** bloquant.

**Ordre** : (1) purge RESTRICT-children du delete-set, (2) DELETE delete_restaurants, (3) DELETE delete_users → CASCADE nettoie le reste.

## 6. Phases & statut

| # | Phase | Statut |
|---|-------|--------|
| 0 | `pg_dump` backup prod (rollback) | ✅ 32M, 128 tables → `oneclick-precleanup-20260623-095844.dump` |
| 1 | ~~Enrichir 7 sans place_id~~ → reporté Phase 7 (les 7 rank-last seront supprimés) | ✅ N/A |
| 2 | Geler keep_restaurants (50) + keep_users en tables | ✅ (intégré au script purge — déterministe) |
| 3 | Restaurer dump → DB scratch `oneclick_dryrun` | ✅ (18115 u / 1286 r) |
| 4 | **Dry-run purge sur scratch** + vérif (0 orphelin FK, counts, **PCC intact**) | ✅ PCC diff=0 ; 5/ville×10 ; →983 u / 287 r. Fix : purge `tenant_memberships` (NO ACTION) avant DELETE users |
| 5 | **Purge PROD** (transaction + assertions) + vérif intégrité | ✅ COMMITTED — 983 u / 287 r ; assertions OK ; PCC intact |
| 6 | PCC accessible depuis OneClick Business (test E2E + fix si besoin) | ✅ owner PCC login OK · `/me tenantId=palmeraie` · `staff/by-user`→resto palmeraie (200). Aucun fix |
| 7 | `force`-enrichir les 50 gardés + `VACUUM ANALYZE` + smoke E2E | ✅ enrich 50/50 (GPS 50, note 49, horaires 39) · VACUUM · smoke 200 · health 200 |

## 7. Garanties anti-régression
- Backup `-Fc` avant toute écriture (rollback `pg_restore`).
- Purge prouvée sur **copie scratch identique** avant la prod.
- Prod en **transaction unique** (ROLLBACK si counts inattendus).
- Vérif **PCC footprint inchangé** (restos/resources/memberships/family/feedbacks/stories) sur scratch ET prod.
- Vérif **0 orphelin FK** post-purge.
- Smoke E2E : login `client1`, login un owner gardé, login staff PCC sur Store.

## 8. Rollback
`pg_restore --clean --if-exists -h localhost -U postgres -d oneclick /home/ubuntu/backups/<fichier>.dump` (depuis le backup Phase 0).

## 9. Journal d'exécution
- **2026-06-23** — Plan validé (option prix). Doc créé. Démarrage Phase 0.
- **2026-06-23** — Phase 0 ✅ backup 32M (128 tables). Phase 1 sautée. Phase 3 ✅ scratch restauré (18115 u / 1286 r).
- **2026-06-23** — Phase 4 ✅ dry-run clean (1 fix : purge `tenant_memberships` NO ACTION avant DELETE users). Phase 5 ✅ purge PROD committé (assertions OK).
- **2026-06-23** — Phase 6 ✅ PCC↔Store vérifié. Phase 7 ✅ enrich 50 + VACUUM + smoke. Scratch droppé. **TERMINÉ.**

## 10. État final livré (2026-06-23)
- **users : 18 115 → 983** (925 RESTAURATEUR · 54 CLIENT [25 explicites + 29 membres PCC] · 3 GROUP_ADMIN · 1 SUPERADMIN).
- **restaurants : 1 286 → 287** = 50 OneClick (5/ville × 10) + 237 palmeraie (PCC, intact).
- **PCC intact** : 237 restos / 221 resources / 28 memberships — **diff 0** sur toutes les tables PCC (restos/resources/memberships/family/feedbacks/stories/bookings).
- **50 OneClick enrichis Google** : GPS 50/50, note 49/50, horaires 39/50 (Google n'a pas d'horaires pour 11), tous frais.
- **0 orphelin FK** · `health=200` · smoke logins client1 / owner OneClick / owner PCC = 200.
- **PCC accessible depuis OneClick Business** : owner PCC login OK, `/me tenantId=palmeraie`, `staff/by-user`→resto palmeraie.
- **Supprimés** : ~16 500 staff + 224 clients + lagrillardiere (17) + restopro (5) + **HOMU (1)** + tenants de test. Rollback : `/home/ubuntu/backups/oneclick-precleanup-20260623-095844.dump`.
- ⚠️ Non touché (comme demandé) : PCC conserve aussi ses ~227 restos soft-deleted + leur staff (~900 users RESTAURATEUR) ; schéma Supabase résiduel (`auth.*`, `user_roles`).

## 11. Suivi post-livraison (2026-06-23 PM)
- **Trim staff** (sur demande) → **1 owner / resto OneClick**. Suppression des **862 non-owners** (serveur/waiter/manager) des 50 restos. Backup `oneclick-pretrim-20260623-103317.dump`. Transaction + assertions (PCC / superadmin / clients25 / owners50 / restos inchangés). **Users : 983 → 121** (63 RESTAURATEUR [50 owners OneClick + 13 PCC] · 54 CLIENT · 3 GROUP_ADMIN · 1 SUPERADMIN). Restaurants inchangés (287).
- **Bug horaires Spotlight (iOS)** : `opening_hours` était stocké au **format Google brut** (`{periods, weekdayDescriptions}`) qu'aucun client (iOS/Android/web) ne parse → horaires vides.
  - **Fix data (live)** : transform SQL Google-brut → **tableau canonique** `[{day,open,close}]` sur les **39** restos OneClick concernés (les 11 sans horaires Google restent vides — limite Google). Tire-pour-rafraîchir sur l'app pour voir le résultat.
  - **Fix cause racine (backend, non déployé)** : `GooglePlacesEnrichmentService.toCanonicalOpeningHours()` émet désormais le tableau canonique au lieu du brut Google (+ 3 tests unitaires verts). ⏳ Part avec le **prochain JAR** (avec aussi le retrait du bouton email d'approbation, en attente de ton go deploy).
