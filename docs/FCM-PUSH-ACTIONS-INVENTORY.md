# Inventaire des actions déclenchant un push FCM — OneClick

> **Date** : 12 juin 2026. **Auteur** : audit automatisé (Spring + legacy Deno + docs), vérifié source par source.
> **Remplace** (sur la partie technique) les docs périmées `OneClick/docs/NOTIFICATIONS-INVENTORY.md` et
> `PUSH-NOTIFICATIONS-STUDY.md` (datées du 9 avril 2026, antérieures au pivot Spring, sans HOMU/PCC/multi-tenant).

## Contexte : 2 systèmes coexistent

- **Backend Spring** (`OneClick_Spring`, monolithe Spring Modulith) — **système cible**. Envoi FCM via
  `core/notification/internal/FcmPushService` (FCM HTTP v1 réel depuis le sprint **B.8.4**, 12/06/2026).
  Mode **stub** (`{sent:0,"FCM not configured"}`, 0 appel réseau) tant que `app.fcm.project-id` /
  `app.fcm.service-account-json` ne sont pas définis.
- **Pile legacy Supabase/Deno** (Edge Functions du worktree `4click-hh` / `reverent-goldstine`) — **toujours en
  production**. Porte aujourd'hui la majorité du push réel.

Mécanisme bas-niveau identique partout : `POST https://fcm.googleapis.com/v1/projects/{id}/messages:send`,
OAuth2 service-account (JWT RS256), nettoyage inline des tokens `UNREGISTERED`/`NOT_FOUND`.

---

## A) Backend Spring (`OneClick_Spring`)

Le push Spring n'est câblé que sur les **4 événements de réservation** (listeners `@ApplicationModuleListener`
dans `NotificationEventHandler` → `notify()` → `pushService.sendReservation`). `actif*` = pousse réellement si
`app.fcm.*` configuré, sinon stub.

| # | Action métier | Déclencheur | Mécanisme | Destinataire | Statut |
|---|---------------|-------------|-----------|--------------|--------|
| 1 | Client crée une résa (pending) | `ReservationCreatedEvent` → `onReservationCreated` | `notify()` → `pushService.sendReservation` | Client | actif* |
| 2 | Resto change le statut → **confirmed / refused / counter_proposed / cancelled / honored / no_show** | `ReservationStatusChangedEvent` → `onReservationStatusChanged` | idem (`titleAndBody()` = `null` pour `pending`/inconnu → pas de push) | Client | actif* — **6 statuts** |
| 3 | Organisateur ajoute un invité **identifié** | `ReservationGuestAddedEvent` (si `guestUserId ≠ null`) | idem | Invité identifié | actif* |
| 4 | Un invité répond (accepté/refusé) | `ReservationGuestRespondedEvent` (si organizerId ≠ null) | idem | Organisateur | actif* |
| 5 | Push promo / campagne | `POST /api/notifications/push/promo` (`@PreAuthorize CREATE:NOTIFICATIONS`, admin) | `pushService.sendPromo` | `userIds` du DTO | **câblé, non auto** (aucun dispatcher) |
| 6 | Push résa par appel direct | `POST /api/notifications/push/reservation` (admin) | `pushService.sendReservation` | `recipientUserId` du DTO | **câblé, non auto** (doublon des listeners) |

*Fichiers clés :* `core/notification/internal/NotificationEventHandler.java`,
`core/notification/NotificationController.java`, `core/notification/internal/FcmPushService.java`,
`modules/reservation/internal/ReservationService.java`, `modules/reservation/internal/ReservationGuestService.java`.

---

## B) Pile legacy Supabase/Deno (en production)

| # | Action métier | Déclencheur | Edge Function | Destinataire |
|---|---------------|-------------|---------------|--------------|
| 7 | Admin approuve une demande promo | UI `AdminNotifRequestsPanel` → invoke | `send-promo-push` | Clients resto **par segment** (tous/fidèles/nouveaux/inactifs/ruby/sapphire/emeraude) |
| 8 | Owner publie une promo (Pro Desk Store) | UI `OfferJetNew` (JWT owner) | `send-promo-push` | Clients resto (segment `tous`) |
| 9 | Admin tenant publie une promo PCC/HOMU | UI `TenantPromos` (JWT admin) | `send-promo-push` (mode tenant) | Tous les membres du tenant |
| 10 | Nouvelle résa (INSERT) | **Trigger DB pg_net** `trg_reservation_push_insert` | `send-reservation-push` | Staff resto (app `store`) |
| 11 | Résa **confirmée** | Trigger DB pg_net UPDATE `status='confirmée'` | `send-reservation-push` | Client organisateur (app `win`) |
| 12 | Résa **refusée** | Trigger DB pg_net | `send-reservation-push` | Client organisateur |
| 13 | **Contre-proposition** | Trigger DB pg_net | `send-reservation-push` | Client organisateur |
| 14 | Résa **annulée** (client) | Trigger DB pg_net | `send-reservation-push` | Staff resto |
| 15 | **No-show** marqué | Trigger DB pg_net | `send-reservation-push` (staff = in-app only) | Client organisateur (score −0.5) |
| 16 | **Rappel J-1** | **pg_cron** `send-reservation-reminders` → event `reminder_j1` | `send-reservation-push` | Organisateur + invités acceptés |
| 17 | **Rappel H-2** | pg_cron → event `reminder_h2` | `send-reservation-push` | Organisateur + invités acceptés |
| 18 | Contestation no-show | UI `useNoShowDispute` → invoke | `send-reservation-push` | Staff resto / staff+admin (escalade) |
| 19 | Push de test diagnostic | bouton UI push-debug | `send-reservation-push` | `target_user_id` explicite |
| 20 | **Demande d'ami** envoyée | UI `CircleAddFriend` | `send-friend-request-push` | Destinataire |
| 21 | Demande d'ami **acceptée** | UI `GlassHeader` | `send-friend-request-push` | Demandeur initial |
| 22 | Demande d'ami **refusée** | UI `GlassHeader` | `send-friend-request-push` | Demandeur initial |
| 23 | Membre PCC : **booking créé** (Padel/Spa/Golf/Coiffeur/Palm Gym) | UI `ResourceBookingPage` → `notifyPccStaff('created')` | `send-pcc-staff-notification` | Staff actifs du resto de la ressource |
| 24 | Membre PCC : **annule** son booking | UI → `notifyPccStaff('cancelled_by_organizer')` | `send-pcc-staff-notification` | Staff actifs du resto |
| 25 | Membre PCC : **demande de séminaire** B2B | UI `PccSeminaires` → `notifyPccStaff('seminar_request')` | `send-pcc-staff-notification` | Tous les staff du tenant |
| 26 | Staff PCC : booking **confirmé** | UI `PccStaffBookings` → `notifyPccClient('confirmed')` | `send-pcc-client-notification` | Client organisateur |
| 27 | Staff PCC : **honoré** (+1 carte fidélité) | UI → `notifyPccClient('honored')` | `send-pcc-client-notification` | Client organisateur |
| 28 | Staff PCC : **no-show** | UI → `notifyPccClient('no_show')` | `send-pcc-client-notification` | Client organisateur |
| 29 | Staff PCC : **annulé** (club) | UI → `notifyPccClient('cancelled')` | `send-pcc-client-notification` | Client organisateur |
| 30 | Expiration **contrat** partenaire (J-30/J-15/J-7) | pg_cron jobid 13 (mensuel) | `notify-expiring-contracts` | Admins — **⚠️ in-app `admin_notifications` SEULEMENT, pas de FCM** + `cron.schedule` non commité |
| 31 | Expiration **points** fidélité (J-7/J-1) | pg_cron jobid 14 (horaire) | `notify-expiring-points` | Clients — **⚠️ in-app SEULEMENT, pas de FCM** + `cron.schedule` non commité |

*Notes :* `send-promo-push` et `send-reservation-push` créent **aussi** une ligne in-app (double canal).
`send-friend-request-push` ne crée pas l'in-app (déjà faite par l'appelant). Filtrage `app_id` (win/store)
appliqué par les EFs résa/ami ; **retiré** dans `send-promo-push` pour atteindre les membres PCC (token `store`
uniquement). Tous les `invoke` UI sont fire-and-forget. `cleanup-stale-push-tokens` = maintenance (DELETE > 30j).

---

## C) ⚠️ Écart Spring vs Legacy (point critique)

Spring ne couvre que les 4 events de réservation. Actions **poussées par le legacy mais ABSENTES du push Spring** :

| Domaine | Legacy pousse | Spring pousse ? |
|---------|---------------|-----------------|
| **Promo / marketing** | #7-9 (auto, segmenté, tenant) | ❌ endpoint `sendPromo` existe (#5) mais **aucun dispatch auto** |
| **Demande d'ami** | #20-22 | ❌ **in-app seulement** (`onFriendshipRequested/Responded`) |
| **PCC bookings / séminaires** | #23-29 | ❌ **ni notif ni push** (`ResourceBooking*`/`Seminar*Event` non écoutés par `NotificationEventHandler`) |
| **Rappels J-1 / H-2** | #16-17 (FCM réel) | ❌ **piège** : crons Spring font un pur `INSERT`, `channel='push'` est trompeur, `pushService` jamais appelé → 0 FCM |
| **Auto-cancel (cron H-2)** | (legacy notifie le staff) | ❌ `UPDATE` SQL natif → bypasse `changeStatus` → n'émet pas l'event → 0 notif |

---

## D) Notifs in-app SEULEMENT (aucun push FCM)

**Spring** (`createInApp` sans `notify`) : demande d'ami + réponse, commentaire/like Circle, OTP conversion points,
Ma Famille PCC, avis + réponse feedback PCC, annonce tenant, séminaire (demande + statut), crons rappels (INSERT only).

**Legacy** : `notify-expiring-contracts`, `notify-expiring-points` (in-app uniquement), `cleanup-stale-push-tokens`.

**UX** : tous les toasts Sonner, tous les `action_logs` (cloche Store/Admin), Snap2Earn scan (type `points`).

---

## E) Recommandations (parité Spring)

1. **Brancher les rappels J-1/H-2 sur `pushService`** dans `ReservationCronJobs` (gap le plus visible client).
2. **Porter le push promo en automatique** (dispatcher de campagne / hook approbation), ou garder le legacy documenté.
3. **Écouter `ResourceBooking*` / `Seminar*Event`** dans `NotificationEventHandler` (parité PCC staff/client).
4. **Sortir le push demande d'ami de l'in-app-only** (`onFriendshipRequested` / `onFriendshipResponded`).
5. **Committer les `cron.schedule` legacy** (jobids 13/14 `notify-expiring-*`) en migration — *hors périmètre Spring*.

*Statut d'avancement des recommandations : voir la section « Suivi » ci-dessous (mise à jour au fil de l'implémentation).*

## F) Suivi de l'implémentation

- [x] **R1 — Rappels J-1/H-2 → push FCM** ✅ (12/06). Les crons publient un `ReservationReminderDueEvent` par résa due → `NotificationEventHandler.onReservationReminderDue` crée la notif in-app (metadata `{reservationId, slot}` pour l'anti-doublon H-2) + push FCM via `FcmPushService`. Nouveaux : `shared/events/ReservationReminderDueEvent`, `NotificationService.createReservationReminder`. Tests : `ReservationCronJobsTest` (5, fan-out), `NotificationServiceTest` (+1, metadata), `ReservationReminderCronIntegrationTest` (2, SQL réel + chaîne event→listener→notif). Suite notification 67 verte + Modulith.
- [x] **R2 — Push promo automatique (parité complète)** ✅ (12/06). `review(approved)` → `PromoApprovedEvent` → **loyalty** résout l'audience du segment (`PromoAudienceResolver` : all/fideles/nouveaux/inactifs + tiers ruby/sapphire/emeraude/black, sur `loyalty_accounts ⨝ loyalty_transactions`/`tiers`) → `PromoAudienceResolvedEvent` → **notification** `FcmPushService.sendPromo` + `markSent`. **100 % event-driven** (zéro dépendance typée loyalty↔notification, zéro changement `allowedDependencies`). Nouveaux : 2 events `shared`, `PromoAudienceResolver`, `PromoApprovedListener` (loyalty), `PromoDispatchListener` (notif) ; trigger dans `PromoNotificationService.review()`. Mapping legacy→Spring : « ticket » = `reason LIKE 'snap2earn%'` ; tier via `loyalty_accounts.tier_id` (cache). Tests : résolveur (6, par segment), 2 listeners (4), intégration cross-module (1, `approved`→`sent` réel). Suite notification 71 verte + Modulith.
- [x] **R3 — Push PCC bookings + séminaires** ✅ (12/06). `onResourceBookingStatusChanged` (nouveau listener) → push client organisateur. `onSeminarRequested` → push chaque staff (`recipientUserIds` déjà sur l'event). `onSeminarStatusChanged` → push membre. **Sans résolveur cross-module** (destinataires portés par les events). Tests : `NotificationEventHandlerPccPushTest`. Suite notification + Modulith verts.
  - **R3-bis (parité 1:1 annulation)** ✅ : ajout du champ `changedBy` à `ResourceBookingStatusChangedEvent` (renseigné par `ResourceBookingService` via `SecurityHelper.currentUserId()`). Le listener route `cancelled` : push client si **tiers** annule (staff/admin, ou système où `changedBy==null`), **pas** de push si auto-annulation membre (`changedBy==organizerId`). `confirmed/completed/no_show` toujours poussés. 7 tests (dont staff-cancel→push, member-cancel→no-push) + `ResourceBookingPunchListenerTest` mis à jour + module resource_booking 51 verts.
- [x] **R4 — Push demande d'ami** ✅ (12/06). `onFriendshipRequested` + `onFriendshipResponded` créent l'in-app **ET** poussent (helper `pushToUser` → `sendPromo` mono-destinataire). Tests : `NotificationEventHandlerFriendPushTest` (2).
- [ ] R5 — Commit `cron.schedule` legacy (jobids 13/14) — **hors périmètre Spring** (migration dans 4click-hh, à valider avec l'équipe legacy).
