# Plan d'exécution — Remédiation notifications (4Click → Spring + iOS natif)

> Plan VIVANT — mis à jour à la fin de CHAQUE lot. Source d'audit : `AUDIT-NOTIFICATIONS-4CLICK-VS-SPRING-IOS.md`.
> Démarré le 17/06/2026. Décisions produit : **toutes OUI** (cf bas de doc).

## Légende statut
- ⬜ à faire · 🔧 en cours · ✅ fait & vérifié · ⏭️ écarté (raison)

## Directives non négociables (rappel à appliquer à chaque lot backend)
1. **Temps réel / dashboards = WebSocket exclusivement** (in-app notifs passent déjà par STOMP `/user/queue/notifications` via `NotificationService.create`).
2. **`hasAuthority(...)` uniquement** — jamais `hasRole`/`isAuthenticated`.
3. **Tests obligatoires** : unitaires isolés **+** intégration, indépendants/reproductibles, services externes mockés.
4. **Monolithe propre** : event (résolution destinataires côté module source) → `@ApplicationModuleListener` dans `NotificationEventHandler` (core.notification CLOSED) → `createInApp` / `pushToUser`. Pas de microservice, pas d'EF dédiée PCC.
5. **Vérif pré-livraison de CHAQUE lot** : compile + tests unit + tests intégration + `ModularityTests` verts + grep `hasRole` = 0 + OpenAPI cohérent.
6. **Je vérifie/teste derrière chaque agent** (build + tests centralisés par l'orchestrateur).

## Modèle d'exécution
- Backend = lots **séquentiels** (hub `NotificationEventHandler` partagé → pas d'écriture concurrente) + porte de vérif après chaque lot.
- Natif iOS = repo séparé → parallélisable, build `xcodebuild` après chaque lot.
- Flyway : prochaine version libre = **V101** (à n'utiliser que si une colonne est réellement requise ; la plupart des lots n'ont PAS de migration).

---

## P0 — Routing natif HIGH (débloque toute la chaîne push staff)
| ID | Lot | Statut | Vérif |
|----|-----|--------|-------|
| N1 | iOS Store — tap-push route vers l'écran (StoreAppDelegate.didReceive + `StorePushRouter` dans target Store) | ✅ | build Store EXIT=0 + StorePushRouterTests 18✅ |
| N2 | iOS Store — cloche header réelle + badge non-lus alimenté (shell-owned StaffNotificationsViewModel) | ✅ | StaffNotificationsViewModelTests 9✅ |
| N3 | iOS Store — inbox realtime (STOMP `/user/queue/notifications`) | ✅ | inclus, build vert |

## P1 — Backend MEDIUM + natif
| ID | Lot | Statut | Vérif |
|----|-----|--------|-------|
| B3 | restaurant-referral resto→resto activé → admins (in-app) — event existe, listener manque | ✅ | 27 tests verts (unit 11 + intég 3 + Modulith 2 + svc 11) |
| B2 | points crédités au scan → client (in-app « points gagnés ») — filtre `snap2earn\|` | ✅ | inclus BE1 |
| B1 | parrainage client activé → parrain + filleul (in-app) | ✅ | `ReferralActivatedEvent`, 45 tests verts BE2 |
| B8 | contestation no-show créée → staff (in-app + push) | ✅ | `NoShowDisputeCreatedEvent` + staff resolver ; fix régression unit test |
| B9 | annulation client/membre → staff (branche staff sur cancelled résa + booking) | ✅ | `changedBy`+`staffRecipientIds` sur events ; corrigé `softDeleteBooking` qui n'émettait aucun event |
| B4/B5 | onboarding resto → admins (in-app) + décision → gérant (email) | ✅ | `StoreOnboardingRequested/DecidedEvent` + email listener ; 17 tests verts (fix régression id @GeneratedValue) |
| N4 | iOS Win — type `seminar` routé (handledTypes + destination `/pocket/pcc/seminaires`→Profil) | ✅ | build Win EXIT=0 + NotificationModelsTests |
| N6 | iOS Win+Store — DELETE token au logout (PushHub.deleteCurrentToken, chemin logout partagé) | ✅ | PushHubTests verts (build+test) |

## P2 — Canaux & secondaires (décisions OUI)
| ID | Lot | Statut | Vérif |
|----|-----|--------|-------|
| B15a | annonce tenant → AUSSI push FCM (legacy=push) | ✅ | `onAnnouncementPublished` +push, filtré toggle `system` |
| B15b | campagne promo → AUSSI ligne in-app par user du segment | ✅ | `PromoDispatchListener` + createInApp |
| B6 | restitution versée → staff (in-app) | ✅ | `RestaurantRestitutionPaidEvent` + endpoint `payRestitution` |
| B13 | annonces programmées (cron publication différée, `publish_at`) | ✅ | `AnnouncementCronJobs` (idempotent via `push_sent_at`), 0 migration |
| P1pref | préférences staff V85 appliquées comme **filtre** (skip si toggle off) | ✅ | `notifyStaff(category)` sur 8 branches staff, défaut ON ; 46 tests verts (fix régression `save()` count) |
| N3b | iOS Store realtime (si pas fait en P0) | ⬜ | build Store |

## P3 — LOW / cleanup
| ID | Lot | Statut | Vérif |
|----|-----|--------|-------|
| B10 | no-show penalty cron (48h) → client (in-app transparence) | ✅ | `ReservationCronJobs.finalizeNoShowPenalties` (metadata anti-dup, pénalité PAS ré-appliquée) ; 108 tests verts BE5 |
| B11 | explore/featured expiré → staff (in-app) | ✅ | `OfferExpiredEvent` dans `PromotionCronJobs` |
| B12 | promo request approuvée/refusée → demandeur (in-app) | ✅ | `PromoRequestReviewedEvent` |
| B14 | invitation convive annulée → invité (in-app) | ✅ | `ReservationGuestRemovedEvent` |
| B7 | don de points (gift) → bénéficiaire (in-app) — OUI V1 | ✅ | `PointsGiftedEvent` (flux gift existait déjà) |
| N5 | iOS Win — destination `announcement` dédiée (`/pocket/annonces`→Profil) | ✅ | build Win + tests |
| N9 | iOS Store — chip filtre **loyalty** (pas `promo` : staff n'a pas ce toggle) | ✅ | StaffNotificationsViewModelTests 19✅ |
| N10 | iOS Win+Store — `DeviceTokenSyncer` placeholder **supprimé** (mort, PushHub gère) | ✅ | build Win+Store EXIT=0 |
| N7/N8 | data-push + routing sous-écran via metadata | ⏭️ DIFFÉRÉ — limitation shell documentée (routing à granularité onglet, pas de path cross-tab). Hors scope (cosmétique tier‑4). N4/N5 atterrissent sur l'onglet Profil. |

---

## Décisions produit (tranchées par l'utilisateur — toutes OUI)
- **GAP-P2** : annonce tenant pousse en FCM ✅ OUI (B15a) ; campagne promo crée aussi une ligne in-app ✅ OUI (B15b).
- **GAP-P1** : préférences staff V85 appliquées réellement comme filtre ✅ OUI (P1pref).
- **GAP-B7** : feature gift-points dans le scope V1 ✅ OUI (B7).
- **GAP-B13** : annonces programmées dans le scope V1 ✅ OUI (B13).

## Journal d'exécution
- 17/06 — Plan créé. Recon : Flyway V100→V101, hub NotificationEventHandler cartographié, B3/B2/B13 raccourcis confirmés.
- 17/06 — **P0 livré & vérifié** (N1/N2/N3) : `StorePushRouter` + `StoreAppDelegate.didReceive` (routing type/link→écran Store), cloche header réelle + badge dérivé du VM partagé, inbox STOMP. Build OneClickStore EXIT=0, tests 18+9 verts.
- 17/06 — **BE1 livré & vérifié** (B3 referral→admins, B2 points-scan→client) : 27 tests verts, hub respecté, 0 migration.
- 17/06 — **BE2 livré & vérifié** (B1 parrainage, B8 dispute→staff, B9 cancel→staff) : 45 tests verts. ⚠️ Régression détectée par ma vérif (unit `NoShowDisputeServiceTest` cassé par le nouveau resolver EntityManager) → **corrigée** (mock EntityManager injecté). Events `ReservationStatusChangedEvent`/`ResourceBookingStatusChangedEvent` enrichis (compat ctors) ; `softDeleteBooking` publie désormais l'event d'annulation membre.
- 17/06 — **BE3 livré & vérifié** (B4/B5 onboarding) : 17 tests verts. Régression `StoreOnboardingServiceTest` (id @GeneratedValue null sous mock) → corrigée.
- 17/06 — **BE4 livré & vérifié** (P2 : B15a/B15b canals, B6 restitution, B13 cron annonces, P1pref filtre staff) : run large 220 tests, 1 régression (`AnnouncementServiceTest` over-spec `save()` count après unification du chemin de publication) → corrigée (times(2)). Filtre staff `notifyStaff(category)` défaut ON. 0 migration (B13 réutilise `push_sent_at` V70).
- 17/06 — **BE5 livré & vérifié** (P3 backend : B10 no-show penalty transparence, B11 offre expirée→staff, B12 promo review→demandeur, B14 invité retiré, B7 gift→bénéficiaire) : 108 tests verts. Flux gift existait déjà. 0 migration.
- 17/06 — **✅ SUITE SPRING COMPLÈTE VERTE : 2890 tests, 0 échec, BUILD SUCCESS** (porte finale backend — aucune régression cross-lot). **TOUT le backend notifications (P1+P2+P3) est livré & vérifié.** Reste : natif iOS (N4/N5/N6/N9/N10) en cours d'agent background → build Win+Store à vérifier.
- 17/06 — **Natif iOS livré & vérifié** (N4 séminaire→Profil, N5 annonce→Profil, N6 DELETE token au logout [chemin partagé Win+Store via PushHub], N9 chip loyalty Store, N10 suppression `DeviceTokenSyncer` mort). ⚠️ 1 erreur de compile test attrapée par ma vérif (`PushHubTests` `private(set)` mutable) → corrigée. **Build OneClickWin EXIT=0 + OneClickStore EXIT=0**, tests NotificationModels (3) + PushHub + StaffNotifications (19) verts.
- 17/06 — **🏁 PLAN TERMINÉ.** Tous les lots P0→P3 livrés & vérifiés (sauf N7/N8 différés = limitation shell documentée, cosmétique). Bilan : Spring 2890 tests verts (0 migration, hasAuthority only, in-app via STOMP, Modulith vert) ; iOS Win+Store builds verts. 4 régressions de sous-agents attrapées par vérif centrale et corrigées. **WIP NON COMMITTÉ** (Spring + iOS natif) — en attente validation user pour commit.
