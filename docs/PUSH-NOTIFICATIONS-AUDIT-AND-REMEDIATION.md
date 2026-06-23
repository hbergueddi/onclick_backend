# Audit push notifications + plan de remédiation (6 trous)

> **Source** : audit profond 4click-hh (legacy prod, lecture seule) vs OneClick_Spring, 2026-06-13.
> **Périmètre** : `OneClick_Spring` (backend) + `OneClick_iOS_Native` (Win client) + `android-native-reconstruction` (Win/Store/PCC).
> **Standards senior appliqués** : dashboards temps réel = WebSocket/STOMP uniquement ; sécurité = `hasAuthority(...)` only (aucun `hasRole`/`isAuthenticated`) ; chaque changement backend = tests **unitaires isolés + intégration** ; monolithe Modulith propre (résolution destinataires côté module source, portée sur l'event ; `core.notification` CLOSED itère seulement).

> **📌 DOCUMENT CANONIQUE notifications/push (architecture Spring).** Il **supersède** les études legacy
> `docs/NOTIFICATIONS-INVENTORY.md` et `docs/PUSH-NOTIFICATIONS-STUDY.md` du repo **4click-hh** (datées 9 avril 2026,
> pré-migration Spring — elles décrivent les Edge Functions Supabase). Ces deux fichiers vivent dans `reverent-goldstine/`
> (= 4click-hh = **prod, hors périmètre d'écriture**) : on **ne peut pas** y poser une bannière depuis ici — à faire dans une
> session 4click-hh dédiée. Côté Spring (in-périmètre), voir aussi `docs/FCM-PUSH-ACTIONS-INVENTORY.md`.

## Contexte architecture (rappel)

Chaîne notif Spring : un module publie un **event** (`shared/events/`) → `NotificationEventHandler` (`@ApplicationModuleListener` = async, after-commit) → `NotificationService.create*` (cloche in-app persistée) + `FcmPushService` (push FCM HTTP v1, stub si `app.fcm.*` absent ou `dispatch-enabled=false`). Le temps réel cloche passe par `UserRealtimePublisher` (STOMP `/queue/notifications`). Les dashboards staff sont déjà en STOMP (`ReservationDashboardPublisher` `/topic/reservations`, `ResourceBookingDashboardPublisher` `/topic/resource-bookings`).

**Frontière Modulith** : `core.notification` ne dépend pas de `identity`/`restaurant`. La résolution « staff actif d'un resto/tenant » se fait par **requête SQL native dans le repo du module source** (cf `AnnouncementRepository.findStaffRecipientIds`, `SeminarRequestRepository.findSeminarRecipientIds`) puis les `List<UUID>` destinataires sont **portés sur l'event**.

## État de la parité (post-audit) — Store→Win = OK, trous ailleurs

L'axe **Store → Win** (action staff → push client) est **100% en parité** : confirm/refuse/contre-proposition/honorée/no-show résa, confirm/honorée/no-show booking PCC, statut séminaire, promo fan-out — tous `push-ok`. Les 6 trous réels sont côté **Win→Store** (staff non prévenu) et **filets cron** (auto-annulations silencieuses, rappels absents) + 1 canal email.

## Les 6 trous + plan par plateforme

| # | Trou | Direction | Sévérité | Régression vs legacy |
|---|------|-----------|----------|----------------------|
| 1 | Résa auto-annulée H-2 (non répondue) → client jamais notifié | cron→Win | 🔴 critique | non (trou des 2 côtés) |
| 2 | Nouvelle résa → **staff non notifié** (Spring notifie le client seul) | Win→Store | 🟠 haute | **oui** |
| 3 | Nouveau booking PCC → **staff non notifié** (aucun event de création) | Win→Store | 🟠 haute | **oui** |
| 4 | Rappels booking PCC J-1/H-2 → client absents (cron n'a que l'expiration) | cron→Win | 🟠 haute | **oui** |
| 5 | Booking PCC auto-annulé → client jamais notifié (UPDATE muet) | cron→Win | 🟠 haute | non |
| 6 | Avis créé / réponse avis → **email brandé** manquant (Spring = in-app only) | Store↔Win | 🟡 basse | oui (canal email) |

### Backend Spring — conception

- **#1** `ReservationCronJobs.expireUnansweredReservations` : SELECT des résas dues (id, client_id, restaurant_id, tenant_id, oldStatus) → UPDATE → publie `ReservationStatusChangedEvent(newStatus="cancelled", reason="auto_expired")` par ligne. Le handler `onReservationStatusChanged` route un message dédié quand `reason="auto_expired"` (« demande non confirmée à temps ») → notif + push client. DRY (réutilise le chemin cancelled).
- **#2** `ReservationService.create` : nouvelle requête `ReservationRepository.findStaffRecipientIdsForRestaurant(restaurantId, clientId)` (SQL natif `restaurant_staffs`) → ajoute `staffRecipientIds` à `ReservationCreatedEvent`. `onReservationCreated` notifie chaque staff (in-app + push, lien `/prodesk/calendrier`) en plus du client.
- **#3** `ResourceBookingService.createBooking` : nouvel event `ResourceBookingCreatedEvent(bookingId, organizerId, resourceId, tenantId, resourceType, staffRecipientIds, occurredAt)` ; staff résolu par `ResourceBookingRepository.findStaffRecipientIdsForTenant(tenantId, organizerId)`. Nouveau listener `onResourceBookingCreated` → notif + push staff (lien `/prodesk/pcc-bookings`). Dashboard staff déjà live (STOMP).
- **#4** `ResourceBookingCronJobs` : ajout `sendResourceBookingRemindersJ1` (`0 0 8 * * *` Africa/Casablanca) + `sendResourceBookingRemindersH2` (`0 0/15 * * * *`). Nouvel event `ResourceBookingReminderDueEvent` + listener `onResourceBookingReminderDue` → `NotificationService.createResourceBookingReminder(... bookingId, slot)` (metadata anti-doublon, calque `createReservationReminder`) + push.
- **#5** `ResourceBookingCronJobs.expireUnansweredResourceBookings` : SELECT (id, organizer_id, resource_id, tenant_id, resource_type) → UPDATE → publie `ResourceBookingStatusChangedEvent(newStatus="cancelled", changedBy=null)`. Le handler existant notifie le client (changedBy=null ≠ organizer → pas de skip).
- **#6** Nouveau `core/email/internal/FeedbackEmailListener` (`@ApplicationModuleListener` sur `FeedbackCreatedEvent` + `FeedbackRepliedEvent`) → résout les emails via `UserDirectoryApi.nameById(...).email()` → `ResendClient.send(...)` brandé (kill-switch api-key + suppression bounce déjà gérés). HTML inline (pas de nouveau template requis).

**Sécurité** : aucun nouvel endpoint → aucune règle `@PreAuthorize` ajoutée (tout est event-driven server-side). Les requêtes staff = lecture de données, pas de contrôle d'accès basé rôle.
**Temps réel** : aucun polling introduit ; cloche via STOMP existant ; dashboards staff déjà STOMP.

### Tests backend (obligatoires, par lot)
- **Unit isolés** (Mockito, pas de DB) : `NotificationEventHandler` — message `auto_expired` (#1), boucle staff résa (#2), listener booking créé (#3), reminder booking due (#4), booking cancelled via `changedBy=null` (#5) ; `FeedbackEmailListener` avec `ResendClient`/`UserDirectoryApi` mockés (#6).
- **Intégration** (`@SpringBootTest` + DB test) : crons #1/#4/#5 publient/inscrivent (idempotence anti-doublon), `ReservationService.create` → staff notifs (#2), `ResourceBookingService.createBooking` → staff notifs (#3).

### Natif Swift (`OneClick_iOS_Native` — Win client uniquement)
Les notifs arrivent via l'API notifications + push FCM ; le client ne fait que **rendre + router**. Travail = mapper les nouveaux `type`/`link` dans `NotificationModels.swift` (icône/label) + le routeur `pendingRoute`. Trous concernés côté **client** : #1 (résa annulée), #4 (rappel booking), #5 (booking annulé). #2/#3 = staff (pas d'app Store iOS encore → hors scope iOS, noté). #6 = email (hors app).

### Natif Android (`android-native-reconstruction` — Win + Store + PCC)
`OneClickMessagingService` (FCM) + mapping type/lien. Trous **client (Win)** : #1, #4, #5. Trous **staff (Store)** : #2 (nouvelle résa → cloche/push Store), #3 (nouveau booking PCC → cloche/push Store). Vérifier que les deep-links staff (`/prodesk/calendrier`, `/prodesk/pcc-bookings`) routent vers les écrans existants.

## Suivi (cocher à l'avancement)

### Backend Spring
- [x] BE-A — #1 résa auto-cancel notif + #2 staff sur nouvelle résa — unit (ReservationCronJobsTest, NotificationEventHandlerReservationGapTest) + intégration (ReservationReminderCronIntegrationTest) **verts**
- [x] BE-B — #3 booking créé→staff + #4 rappels J-1/H-2 booking + #5 booking auto-cancel notif — unit (ResourceBookingCronJobsTest, NotificationEventHandlerBookingGapTest) + intégration (ResourceBookingReminderCronIntegrationTest) **verts**
- [x] BE-C — #6 FeedbackEmailListener (created→owners, reply→member) — unit (FeedbackEmailListenerTest 4/4) **vert** ; `core.email` allowedDependencies += `core.identity`
- [x] BE-D — tests ciblés **verts** + ModularityTests 2/2 + 0 nouveau `hasRole`/`isAuthenticated` + aucun DTO/endpoint modifié (OpenAPI inchangé). **WIP non committé.**

### Natif iOS (Win)
- [x] iOS — **aucun changement requis** : les 6 notifs client (#1/#4/#5) sont de type `reservation`, déjà mappé (`NotificationModels.swift` → icône calendrier) + routé vers l'onglet Réservations (`NotificationsView`). Les nouveaux push apparaissent dans la cloche + routent correctement, sans code. #2/#3 = staff → pas d'app Store iOS (hors scope, noté). #6 = email (hors app).

### Natif Android (Win + Store)
- [x] AND — client #1/#4/#5 déjà routés (type `reservation`, lien `/pocket/oneclick` → `reserveFragment`). Staff #2/#3 : `OneClickMessagingService` route désormais les liens `/prodesk/*` vers `nav_graph_store` + `StoreMainActivity` (`/prodesk/calendrier`→`storeCalendarFragment`, `/prodesk/pcc-bookings`→`storePccStaffBookingsFragment`). **`:app:compileWinDebugJavaWithJavac` vert (JBR 21).**

## Challenge — candidats push « net-new » (events publiés sans listener)

| # | Candidat | État | Implémentation |
|---|----------|------|----------------|
| CH-1 | Nouvelle offre → favoriteurs | ✅ **fait** | `OfferCreatedEvent.favoriteRecipientIds` (résolus si `push_notify=true` via `user_favorites`) → `onOfferCreated` (type `promotion`, lien `/pocket/promos`) |
| CH-2 | Contestation no-show résolue → client | ✅ **fait** | listener `onDisputeResolved(NoShowDisputeResolvedEvent)` (accepté/refusé, type `reservation`) — event déjà publié, 0 changement event |
| CH-3 | Montée de palier fidélité → client | ✅ **fait** | nouveau `TierReachedEvent` publié par `LoyaltyService.earnPoints` au **franchissement** (palier via table `tiers` par tenant, conforme règle DB) → `onTierReached` (type `loyalty`, lien `/pocket/vault`) |
| CH-4 | Avis post-visite honorée → client | ⛔ **flaggé** | aucun flux d'avis **client→restaurant** en OneClick standard (`client_ratings` = resto note client, pas l'inverse) → un push « donnez votre avis » pointerait vers une destination inexistante. **Prérequis : créer la feature de soumission d'avis** avant de câbler le push. |
| CH-5 | « Table prête » (call-ahead) | ⛔ **flaggé** | aucun event ni domaine (pas de file d'attente / check-in / signal table-prête). **Prérequis : nouveau domaine call-ahead** (entité + endpoint staff « table prête » + UI client) avant tout push. |

Tests CH : unit `NotificationEventHandlerChallengeTest` (6) + `LoyaltyServiceTest` tier-crossing (2) ; intégration `LoyaltyFlowIntegrationTest` (18, valide le nouveau JPQL `sumBalanceByClientAndTenant` + flux earn) ; ModularityTests 2/2. **WIP non committé.**

## Suite (conseils #2/#3/#4/#5 exécutés) — durcissement loyalty + vérif push

Après les 6 trous + le challenge (CH-1/2/3), exécution des recommandations « pour la suite » (WIP non committé) :

| # | Sujet | Statut | Détail |
|---|-------|--------|--------|
| CL-2 | **Source unique du palier** | ✅ fait | Le wallet pass calculait le palier avec des seuils **hardcodés** (Ruby/Sapphire/Émeraude/Black) tandis que CH-3 lisait la table `tiers` — divergence possible. Nouveau `LoyaltyTierResolver` (loyalty.internal) : **DB d'abord** (table `tiers` par tenant, jamais seedée par migration → admin-CRUD) **+ fallback canonique** (mêmes seuils qu'avant → 0 changement de comportement par défaut). `WalletPassService` (résout le tenant public `oneclick` via `TenantDirectoryApi.findIdBySlug`) **et** `LoyaltyService.earnPoints` (CH-3) l'utilisent → wallet + push « palier atteint » + `useClientTier` front s'accordent. Tests : `LoyaltyTierResolverTest` (5), `WalletPassServiceTest` (+1 preuve DB-tiers), `LoyaltyServiceTest` tier (2). |
| CL-3 | **Gate enrollment (earn membres-only sur tenant gated)** | ✅ fait | `LoyaltyService.earnPoints` : après `findOrCreateInternal`, si le resto appartient à un tenant **à adhésion** (slug ≠ `oneclick`) **et** le client n'est pas membre actif (`MembershipDirectoryApi.isActiveMember`) → `ForbiddenException` (403). Le throw annule la transaction (`@Transactional`) — aucun compte/crédit orphelin. Tenant public = pas de gate. Ferme la fuite « non-membre accumule sur un resto PCC » côté **accrual** (le résolveur d'audience promo était déjà gardé). Tests : unit (`LoyaltyServiceTest` : non-membre 403 / membre crédite / public sans check) + intégration (`LoyaltyFlowIntegrationTest.earn_gatedTenant_nonMemberForbidden_memberAllowed`, 403 + 2xx déterministes). `restoTenant()` du test corrigé pour cibler un resto **public** (sinon les tests earn/snap2earn 403aient — preuve live que le gate fonctionne). |
| CL-4 | **Smoke push E2E (device)** | ✅ prérequis OK, envoi via `--push` | Backend live up (`localhost:8083`, profil **`enterprise`** → DB **`oneclick_enterprise`**). **CORRECTION** d'un constat initial erroné : le backend live tourne sur `oneclick_enterprise` = **schéma canonique V97** (`restaurant_staffs` pluriel + `tenant_memberships` + `tiers` présents), **104 device_tokens dont 39 staff** → **#2/#3 sont pleinement testables sur le live**. (Le `oneclick_local` que j'avais d'abord interrogé est le DB du profil **`--dev` legacy, inutilisé** par le backend — sa staleté n'impacte rien.) Seul « blocage » restant = `FcmPushService` en **stub par défaut** (`dispatch-enabled=false`), mais c'est un **garde-fou volontaire**, pas un bug : les **clés FCM sont déjà présentes et valides** dans le process live (`FCM_PROJECT_ID`+`FCM_SERVICE_ACCOUNT_JSON`). Activation de l'envoi réel = **`./run.sh --push`** (ajouté). |
| CL-5 | **Docs notif rafraîchies** | ✅ partiel | Bannière canonique ajoutée ici. Docs legacy 4click-hh (hors périmètre) → flag pour session dédiée (cf bannière en tête). |

### Runbook smoke push device (à exécuter manuellement, hors sandbox)
1. **DB** : rien à faire — le backend `enterprise` est déjà sur `oneclick_enterprise` (canonique V97, 39 staff avec token). _(Le `--dev`/`oneclick_local` legacy est stale mais inutilisé ; ne pas le confondre.)_
2. **Lancer en mode push réel** : `./run.sh --push` (relance le backend avec `FCM_DISPATCH_ENABLED=true` ; les clés `FCM_PROJECT_ID`/`FCM_SERVICE_ACCOUNT_JSON` `oneclick-129bb` sont déjà dans l'env). ⚠️ Au boot, des events Modulith dormants peuvent partir en push réel vers les 104 devices — n'activer que pour un test contrôlé.
3. **Token staff** : un staff a déjà un token (39 enregistrés) ; sinon ouvrir l'app **Store** → `POST /api/notifications/tokens` (vérifier via `GET /api/notifications/tokens/by-user/{staffId}`).
4. **#2** : créer une réservation client (Win) sur un resto de ce staff → le staff doit recevoir cloche + push (lien `/prodesk/calendrier`).
5. **CH-2** : résoudre une contestation no-show → le client doit recevoir le push (accepté/refusé).
6. Observer côté serveur : log `FcmPushService` (envoi réel vs stubReason) + `device_tokens` invalides nettoyés.

## Journal
- 2026-06-13 — Audit + doc créés.
- 2026-06-13 — **Les 6 trous traités.** Backend Spring (BE-A/B/C) : 6 events/listeners/crons + repos + email listener, unit + intégration **verts**, ModularityTests 2/2, 0 nouveau `hasRole`, OpenAPI inchangé. Natif iOS = no-op vérifié ; Android = routing staff câblé + compile vert. **Tout en WIP non committé** (Spring + android-native-reconstruction).
- 2026-06-13 — **Challenge CH-1/2/3 livrés** (offre→favoriteurs, dispute→client, palier→client) ; CH-4/5 mis de côté (prérequis feature).
- 2026-06-13 — **Suite CL-2/3/4/5.** CL-2 (source unique palier : `LoyaltyTierResolver` DB+fallback, wallet+CH-3 alignés) + CL-3 (gate enrollment earn membres-only sur tenant gated, 403) : tests unit + intégration **verts** (LoyaltyTierResolver 5, WalletPassService 4, LoyaltyService 52, NotificationEventHandlerChallenge 6, LoyaltyFlow 19 dont gate, +6 RBAC/flow loyalty = 32, ModularityTests 2/2). 0 nouveau `hasRole`, OpenAPI inchangé (event-driven). CL-4 = push device différé (stub FCM + dev DB stale ; code prouvé par intégration + gate 403 live). CL-5 = bannière canonique (docs legacy 4click-hh hors périmètre). **WIP non committé.**
- 2026-06-13 — **Correction CL-4 + fix env (« règle les deux »).** Investigation : le backend live (`localhost:8083`) tourne en profil **`enterprise` → `oneclick_enterprise`** = **canonique V97** (39 staff avec token) — mon constat « dev DB stale bloque #2/#3 » était une **mauvaise attribution** : j'avais interrogé `oneclick_local` (profil `--dev` legacy, **inutilisé**). #2/#3 sont donc testables tels quels. Côté FCM : creds `oneclick-129bb` **déjà valides** dans le process live, seul `FCM_DISPATCH_ENABLED` manquait (garde-fou volontaire). **Fix** : ajout du mode opt-in **`./run.sh --push`** (force `FCM_DISPATCH_ENABLED=true`, avertit de l'envoi réel + events dormants ; safe-by-default préservé ; `bash -n` OK). Aucun rebuild de `oneclick_local` (legacy inutilisé, by-design prod-dump). **WIP non committé.**
