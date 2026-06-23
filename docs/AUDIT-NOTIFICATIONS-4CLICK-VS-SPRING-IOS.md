# Audit notifications — 4Click (legacy) vs Spring + iOS natif (Win/Store)

> Auditeur senior — synthèse de 6 inventaires (3 slices legacy 4Click, 1 map capacités Spring, iOS Win, iOS Store).
> Date : 17/06/2026. Périmètre Spring = `OneClick_Spring` (code actuel, WIP remédiation 13/06 inclus). Natif = `OneClick_iOS_Native` (Win + Store).
> Doc complémentaire (remédiation antérieure) : `OneClick_Spring/docs/PUSH-NOTIFICATIONS-AUDIT-AND-REMEDIATION.md`.

## Contexte de fusion PCC → générique

Dans le legacy, PCC/HOMU/OneClick sont des **tenants whitelabel séparés** (apps « My PCC »/« PCC Store » + Edge Functions `send-pcc-*`/`send-homu-*`). Côté Spring+natif, **tout est fusionné** : 1 app **Win** (ma.oneclick, révèle les programmes via membership) + 1 app **Store** (ma.oneclick.business, staff scoping par tenant). Donc un flux legacy `send-pcc-feedback` est **couvert** dès lors que le module générique Spring (`modules/feedback`) émet la notif tenant-scopée — il ne doit JAMAIS exister d'EF/listener dédié palmeraie. Le scoping tenant passe par la ressource / le resto / le profil, le branding email par slug (`TenantDirectoryApi`).

Ce mapping est globalement **bien réalisé** côté Spring (events génériques) et côté natif (categorize tenant-agnostic `pcc_*` → types `reservation`/`community`/`feedback`/`booking`).

## Verdict global

L'ossature backend est **mûre et quasi-complète** : le `NotificationEventHandler` (hub `core.notification` CLOSED) couvre 24 listeners, le merge PCC→générique est fait, et la remédiation du 13/06 (gaps #1-#6 + challenges CH-1/2/3 push net-new) est présente dans le code actuel. **Les trous résiduels sont majoritairement (a) natifs** — surtout **iOS Store** dont le tap-push ne route pas et dont la cloche header est un placeholder — **et (b) une poignée de flux backend secondaires non portés** (parrainage client, points crédités au scan, restitution versée, onboarding resto, expiration explore/featured, no-show penalty cron). Aucun trou HIGH côté flux métier central client (résa/booking/fidélité/feedback). 2 décisions produit restent à trancher (push vs in-app sur certains flux, et application réelle des préférences staff V85).

---

## directionMatrix

### win → store (client/membre déclenche → staff reçoit)

| Notif | Legacy | Spring (in-app/push) | iOS Win | iOS Store | Verdict |
|---|---|---|---|---|---|
| Nouvelle réservation resto → staff | push+in-app (trigger DB) | ✅ in-app+push (`ReservationCreatedEvent.staffRecipientIds` → `onReservationCreated`) | n/a (cible Store) | ⚠️ inbox affiche (type `reservation`), mais tap-push ne route pas | ⚠️ |
| Réservation annulée par client → staff | push+in-app | ✅ in-app+push (`ReservationStatusChangedEvent` cancelled → notifie staff ? **voir gap**) | n/a | ⚠️ inbox oui | ⚠️ |
| Nouveau booking PCC → staff | push+in-app (`send-pcc-staff-notification`) | ✅ in-app+push (`ResourceBookingCreatedEvent.staffRecipientIds` par tenant) | n/a | ⚠️ inbox oui (type→booking), tap-push ❌ | ⚠️ |
| Booking PCC annulé par membre → staff | push+in-app | ⚠️ couvert si `ResourceBookingStatusChangedEvent(cancelled, changedBy=member)` notifie staff — **non vérifié, voir gap** | n/a | ⚠️ inbox | ⚠️ |
| Nouvelle demande séminaire B2B → staff | push+in-app (`send-pcc-staff-notification`) | ✅ in-app+push (`SeminarRequestedEvent.recipientUserIds` → `onSeminarRequested`) | n/a | ⚠️ inbox (type→booking via pcc_seminar*), tap-push ❌ | ⚠️ |
| Avis/feedback membre soumis → owners+tenant-admins | in-app+email (`send-pcc-feedback`) | ✅ in-app+email Resend (`FeedbackCreatedEvent` → `onFeedbackCreated` + `FeedbackEmailListener`) | n/a | ⚠️ inbox (pcc_feedback*→feedback, chip Avis), tap-push ❌ | ⚠️ |
| Contestation no-show créée → staff | push+in-app (`type=test`) | ❌ aucun event/notif staff sur création de dispute | n/a | ❌ | ❌ |

### store → win (staff/admin déclenche → client/membre reçoit)

| Notif | Legacy | Spring (in-app/push) | iOS Win | iOS Store | Verdict |
|---|---|---|---|---|---|
| Résa confirmée/refusée/contre-prop/no-show → client | push+in-app | ✅ in-app+push (`ReservationStatusChangedEvent` → `onReservationStatusChanged`) | ✅ type `reservation` → onglet Réservations | n/a | ✅ |
| Booking PCC confirmé/honoré/no-show/annulé-club → membre | push+in-app (`notifyPccClient`) | ✅ in-app+push (`ResourceBookingStatusChangedEvent` → `onResourceBookingStatusChanged`, type `reservation`) | ✅ type `reservation` → onglet Réservations | n/a | ✅ |
| Rappel résa J-1/H-2 → client | push+in-app (cron) | ✅ in-app+push (`ReservationReminderDueEvent`) | ✅ | n/a | ✅ |
| Rappel booking PCC J-1/H-2 → membre | push+in-app (cron) | ✅ in-app+push (`ResourceBookingReminderDueEvent`, gap #4) | ✅ type `reservation` | n/a | ✅ |
| Réponse feedback (Adil) → membre | in-app+email (`send-pcc-feedback-reply`) | ✅ in-app+email (`FeedbackRepliedEvent`, type `community`), **pas de push** | ✅ type `community` → onglet Communauté | n/a | ✅ |
| Promo/offre push (segment ou favoris) → client | push+in-app (`send-promo-push`) | ✅ push (`promo_campaign` PUSH SEUL, segment) + ✅ in-app+push (`OfferCreatedEvent` favoris) | ✅ type `promo`/`promotion` → onglet Explore | n/a | ⚠️ (voir gap divergence in-app campagne) |
| Statut séminaire (demandee→annulee) → demandeur | push+in-app (`send-pcc-seminar-status-update`) | ✅ in-app+push (`SeminarStatusChangedEvent`, type `seminar`) | ⚠️ type `seminar` **non listé** dans handledTypes Win → fallback system/no-op | n/a | ⚠️ |
| Dispute no-show résolue → client | push+in-app (`type=test`) | ✅ in-app+push (`NoShowDisputeResolvedEvent`, CH-2, type `reservation`) | ✅ type `reservation` | n/a | ✅ |
| Code OTP conversion points → client | (legacy: in-app) | ✅ in-app SEUL (`RedemptionOtpRequestedEvent`, type `loyalty`) | ✅ type `loyalty` → onglet Profil | n/a | ✅ |
| Points crédités au scan ticket → client | in-app (`snap2earn`) | ❌ aucune notif « points gagnés » (earnPoints n'émet pas de notif cloche) | n/a (type loyalty routerait) | n/a | ❌ |
| Invitation enrollment membre → email | email branded (`send-pcc/homu-enrollment-invite`) | ✅ email Resend générique (`MemberEnrollmentInvitedEvent`, branding par slug) | n/a (email) | n/a | ✅ |
| Décision onboarding resto → gérant (email) | email VPS (`send-onboarding-decision`) | ❌ aucun flux email décision onboarding (provisioning via `member_enrollment`/`tenant_admin_invite` distinct) | n/a | n/a | ❌ |
| Restitution versée → staff | in-app (RPC bulk) | ❌ aucune notif staff sur versement restitution | n/a | ⚠️ inbox afficherait si émis | ❌ |

### store → store (staff/admin/système → staff/admin)

| Notif | Legacy | Spring (in-app/push) | iOS Win | iOS Store | Verdict |
|---|---|---|---|---|---|
| Annonce tenant publiée → staff | push+in-app (`manage-announcement`) | ✅ in-app SEUL (`AnnouncementPublishedEvent`, type `announcement`) — **legacy faisait du push, Spring non** | n/a | ⚠️ deep-link `/prodesk/announcements` mappé, mais cloche header = placeholder + tap-push ❌ | ⚠️ |
| No-show enregistré (log silencieux) → staff | in-app silencieux | ❌ pas de notif log staff sur no_show (Spring notifie le client seul) | n/a | n/a | ❌ (LOW) |
| Promo request approuvée/refusée → demandeur | in-app | ❌ pas de notif au demandeur sur review (review publie `PromoApprovedEvent` pour fan-out, pas de notif au requester) | n/a | ⚠️ inbox | ❌ (LOW) |
| Contrat partenaire expire J-30/15/7 → admins | in-app (`admin_notifications` broadcast) | ✅ in-app+push (`ContractExpiringSoonEvent.recipientAdminIds`=SUPERADMIN, type `system`) | n/a | ⚠️ inbox (deep-link `/galaxy/contrats` n'est pas `/prodesk/*` → toast) | ⚠️ |
| Restaurant referral (resto→resto) activé → admins | in-app (`admin_notifications`) | ❌ `RestaurantReferralActivatedEvent` consommé par loyalty (crédit pts) mais **aucun listener notification** | n/a | n/a | ❌ |
| Onboarding request resto → admins | in-app+email (`send-onboarding-request`) | ❌ `StoreOnboardingService` n'émet ni event ni notif admin | n/a | n/a | ❌ |
| Annonce programmée publiée (cron) → staff | push+in-app (cron) | ⚠️ annonce immédiate ✅ ; **programmée via cron = « V1 hors scope »** (non implémenté) | n/a | ⚠️ | ⚠️ |
| Invitation admin tenant → email | (legacy via onboarding) | ✅ email Resend (`TenantAdminInvitedEvent`, branding slug) | n/a | n/a | ✅ |
| Accusé lecture annonce | état (mark-read) | ✅ endpoint mark-read (pas une notif sortante) | n/a | ✅ inbox mark-read | ✅ |
| Préférences notif staff (5 toggles) | table UI-only V1 | ⚠️ table V85 + service self-service, **pas branché comme filtre** dans les listeners | n/a | ⚠️ écran StaffNotifications | ⚠️ |
| Explore/Featured expiré → staff | in-app (`expire-promotions`) | ❌ `PromotionCronJobs.expirePromotions` désactive sans notif staff | n/a | n/a | ❌ (LOW) |

### win → win (client → client)

| Notif | Legacy | Spring (in-app/push) | iOS Win | iOS Store | Verdict |
|---|---|---|---|---|---|
| Demande d'ami → destinataire | push+in-app | ✅ in-app+push (`FriendshipRequestedEvent`, R4, type `friend_request`) | ✅ popup in-panel Accepter/Refuser | n/a | ✅ |
| Réponse demande d'ami → demandeur | push+in-app | ✅ in-app+push (`FriendshipRespondedEvent`, type `community`) | ✅ onglet Communauté | n/a | ✅ |
| Invitation convive → invité | in-app (PAS de push legacy) | ✅ in-app+push (`ReservationGuestAddedEvent`, type `reservation`) — **Spring AJOUTE le push** | ✅ type `reservation` | n/a | ✅ |
| Invité accepte/décline → organisateur | (legacy: in-app guest réponse) | ✅ in-app+push (`ReservationGuestRespondedEvent`) | ✅ | n/a | ✅ |
| Invitation convive annulée → invité | in-app | ❌ pas d'event/notif d'annulation d'invitation convive | ⚠️ | n/a | ❌ (LOW) |
| Parrainage client activé → parrain (+50 pts) | in-app (trigger DB) | ❌ `referrals` activate crédite pts mais aucune notif parrain | ⚠️ type referral routerait | n/a | ❌ |
| Parrainage client activé → filleul (+50 pts) | in-app (trigger DB) | ❌ idem, pas de notif filleul | ⚠️ | n/a | ❌ |
| Don de points (gift) → bénéficiaire | in-app (`gift-points`) | ❌ pas de flux gift-points/notif don entre membres | ⚠️ type loyalty routerait | n/a | ❌ |
| Cercle PCC — like d'un post → auteur | in-app (trigger DB) | ✅ in-app SEUL (`MemberPostLikedEvent`, type `community`) | ✅ onglet Communauté | n/a | ✅ |
| Cercle PCC — commentaire → auteur | in-app (trigger DB) | ✅ in-app SEUL (`MemberPostCommentedEvent`) | ✅ | n/a | ✅ |
| Cercle PCC — mention (commentaire/post) → mentionné | in-app (trigger DB) | ⚠️ mentions commentaire couvertes (`mentionedRecipientIds`) ; **mention dans le corps d'un post à l'approbation = non vérifié** | ✅ community | n/a | ⚠️ |
| Ma Famille — ajout d'un proche → proche | (PCC natif Lot 5) | ✅ in-app SEUL (`FamilyMemberAddedEvent`, type `community`) | ✅ | n/a | ✅ |

### système → win / système → store (cron, pas d'expéditeur humain)

| Notif | Legacy | Spring (in-app/push) | iOS Win | iOS Store | Verdict |
|---|---|---|---|---|---|
| Résa auto-annulée H-2 (non répondue) → client | in-app (cron) | ✅ in-app+push (`ReservationStatusChangedEvent reason=auto_expired`, gap #1, message dédié) | ✅ type `reservation` | n/a | ✅ |
| Booking PCC auto-annulé H-2 → membre | push+in-app (cron) | ✅ in-app+push (`expireUnansweredResourceBookings` → `ResourceBookingStatusChangedEvent changedBy=null`, gap #5) | ✅ | n/a | ✅ |
| Points expirent J-7 / J-1 → client | in-app SEUL (cron) | ✅ in-app+push (`PointsExpiringSoonEvent`, Feature A — Spring AJOUTE le push) | ✅ type `loyalty` | n/a | ✅ |
| Points effectivement expirés → client | in-app (cron) | ⚠️ alerte pré-expiration ✅ ; notif « points expirés » post-fait **non vérifiée** | ⚠️ | n/a | ⚠️ |
| Palier fidélité atteint → client | in-app (snap2earn) | ✅ in-app+push (`TierReachedEvent`, CH-3) | ✅ type `loyalty` | n/a | ✅ |
| No-show penalty appliquée (48h, non contesté) → client | in-app silencieux (cron) | ❌ pas de cron penalty + notif transparence | ⚠️ | n/a | ❌ (LOW) |

---

## gaps (détail)

Voir le tableau structuré dans la sortie de l'audit. Résumé par catégorie :

**Trous BACKEND (Spring n'émet pas) — flux legacy non portés :**
- `GAP-B1` parrainage client activé → parrain + filleul (in-app +50 pts). MEDIUM.
- `GAP-B2` points crédités au scan ticket → client (in-app « points gagnés »). MEDIUM (UX fidélité importante).
- `GAP-B3` restaurant referral resto→resto activé → admins (in-app). MEDIUM (l'event existe déjà, listener notif manquant).
- `GAP-B4` onboarding request resto → admins (in-app+email). MEDIUM.
- `GAP-B5` décision onboarding → gérant (email). MEDIUM.
- `GAP-B6` restitution versée → staff (in-app). LOW/MEDIUM.
- `GAP-B7` don de points (gift) → bénéficiaire (in-app). LOW (feature gift-points peut-être hors scope V1).
- `GAP-B8` contestation no-show créée → staff (push). MEDIUM.
- `GAP-B9` annulation booking PCC par membre → staff + annulation résa par client → staff : confirmer que le statut cancelled notifie bien le staff (et pas seulement le client). MEDIUM.
- `GAP-B10` no-show penalty cron (48h) → client (in-app transparence). LOW.
- `GAP-B11` explore/featured expiré → staff (in-app). LOW.
- `GAP-B12` promo request approuvée/refusée → demandeur (in-app modération). LOW.
- `GAP-B13` annonce programmée via cron (V1 hors scope). LOW/MEDIUM selon usage.
- `GAP-B14` invitation convive annulée → invité (in-app). LOW.
- `GAP-B15` divergence canal : annonce tenant (legacy push, Spring in-app seul) ; campagne promo (legacy push+in-app, Spring push seul, pas de ligne in-app). À trancher produit. MEDIUM.

**Trous NATIF (Spring émet, app ne route/affiche pas) :**
- `GAP-N1` iOS Store — **tap-push ne route vers AUCUN écran** (`StoreAppDelegate` n'implémente pas `didReceive`, `NotificationPushRouter` exclu du target Store). HIGH.
- `GAP-N2` iOS Store — **cloche header = placeholder** (vrai inbox seulement via Plus → Notifications) + **badge non-lus jamais alimenté** (`setUnreadCount` non appelé). HIGH.
- `GAP-N3` iOS Store — pas de live/realtime inbox (topic STOMP non câblé) → refresh manuel requis. MEDIUM.
- `GAP-N4` iOS Win — type `seminar` **absent de handledTypes** → notif statut séminaire tombe en system/no-op. MEDIUM (les membres PCC reçoivent ces notifs).
- `GAP-N5` iOS Win — type `announcement` sans destination dédiée → tap no-op si link non `/pocket/*`. LOW.
- `GAP-N6` iOS Win + Store — **DELETE token au logout jamais appelé** → device_token reste actif (risque fan-out vers mauvais user). MEDIUM (sécurité/correction).
- `GAP-N7` iOS Win — pas de gestion data-push (refresh dépend du ping STOMP/REST). LOW.
- `GAP-N8` iOS Win/Store — routing onglet-only (pas de sous-écran via metadata, omis du DTO). LOW.
- `GAP-N9` iOS Store — chips de filtre incomplets (loyalty/promo sans chip dédié). LOW.
- `GAP-N10` iOS Win/Store — `DeviceTokenSyncer` placeholder no-op = code mort à supprimer/brancher. LOW.

**Trous PRODUIT (à trancher) :**
- `GAP-P1` Préférences staff V85 : table + service self-service existent mais **ne filtrent pas** les listeners/FcmPushService. Décision : implémente-t-on la suppression conditionnelle (gap #5 audit 13/06) ? MEDIUM.
- `GAP-P2` Canaux : push vs in-app sur annonce tenant (B15a) et in-app sur campagne promo (B15b). Décision produit.

---

## plan

### P0 — Routing natif HIGH (débloque toute la chaîne push staff)
1. **`GAP-N1` iOS Store tap-push routing** : implémenter `userNotificationCenter(_:didReceive:)` dans `StoreAppDelegate` ; inclure un routeur push équivalent à `NotificationPushRouter` dans le target Store (project.yml — ajouter le fichier de routing à la compilation Store) ; brancher `deliver(link)` → `StoreNotificationDeepLink.destination`. Sans ça, **toute** notif push staff (nouvelle résa/booking/séminaire/feedback) ouvre l'app sans naviguer.
2. **`GAP-N2` iOS Store cloche + badge** : câbler la cloche header (`GlassHeaderView.bellTapped` → vrai `StaffNotificationsView` au lieu du placeholder) ; alimenter `setUnreadCount` depuis le VM (dérivé de la liste comme Win) pour que la pastille s'affiche.

### P1 — Backend MEDIUM (flux métier secondaires manquants)
3. **`GAP-B3` restaurant referral → admins** : ajouter `onRestaurantReferralActivated(RestaurantReferralActivatedEvent)` dans `NotificationEventHandler` → `createInApp` (type `system`, lien `/galaxy/contrats` ou `/restaurants`) vers SUPERADMIN. L'event existe déjà → coût minimal.
4. **`GAP-B2` points crédités au scan → client** : sur `LoyaltyEarnedEvent` (ou un nouveau champ sur l'event), `createInApp(clientId, "loyalty", …)` (lien `/pocket/vault`). In-app seul (parité legacy). Attention : ne pas doubler avec `tier_reached`.
5. **`GAP-B1` parrainage client → parrain + filleul** : sur l'activation referral (`SocialController.activate`), publier un event → `createInApp` aux 2 users (type `referral`/`community`). In-app seul.
6. **`GAP-B8` contestation no-show créée → staff** : publier un event sur création de dispute → `createInApp`+push staff du resto (réutiliser `findStaffRecipientIdsForRestaurant`).
7. **`GAP-B9` annulation → staff** : vérifier/garantir que `ReservationStatusChangedEvent(cancelled)` et `ResourceBookingStatusChangedEvent(cancelled, changedBy=member)` notifient AUSSI le staff (pas seulement le client). Ajouter la branche si manquante.
8. **`GAP-B4`/`GAP-B5` onboarding resto** : émettre `OnboardingRequestedEvent` (→ in-app admins + email) et `OnboardingDecidedEvent` (→ email gérant via `core.email`). Pattern = `MemberEnrollmentInviteEmailListener`.
9. **`GAP-N4` iOS Win type `seminar`** : ajouter `seminar` à `handledTypes` Win + route vers un écran/onglet (Profil ou Réservations). Les membres PCC reçoivent ces notifs.
10. **`GAP-N6` DELETE token au logout** (Win + Store) : appeler `NotificationRepository.deleteToken` dans `logout()`. Sécurité/correction — éviter fan-out vers un appareil délogué.
11. **`GAP-N3` iOS Store realtime inbox** : câbler l'abonnement STOMP `/user/queue/notifications` côté Store (calque Win).

### P2 — Backend MEDIUM/produit (canaux & restitution & annonce programmée)
12. **`GAP-B15`/`GAP-P2` canaux** — décision produit : (a) annonce tenant doit-elle pousser en FCM (legacy oui) ? (b) campagne promo doit-elle aussi créer une ligne in-app (legacy oui, Spring push seul) ? Si oui : `onAnnouncementPublished` → ajouter `pushToUser` ; `PromoDispatchListener` → ajouter `createInApp` par user du segment.
13. **`GAP-B6` restitution versée → staff** : sur le versement (`LoyaltyExtensionService` restitutions), `createInApp` staff du resto (type `restitution`/`system`).
14. **`GAP-B13` annonce programmée (cron)** : implémenter le cron de publication différée (calque `publish-scheduled-announcements`, auth CRON_SECRET, garde created_at > now-6h). Réutilise `AnnouncementPublishedEvent`.
15. **`GAP-P1` préférences staff V85** : implémenter le filtre dans `NotificationEventHandler`/`FcmPushService` (skip si toggle off). Décision : suppression côté producteur (in-app + push) ou affichage seul ?

### P3 — LOW (cosmétique / edge / cleanup)
16. **`GAP-B10`** no-show penalty cron + notif transparence client (in-app).
17. **`GAP-B11`** explore/featured expiré → staff (in-app).
18. **`GAP-B12`** promo request approuvée/refusée → demandeur (in-app).
19. **`GAP-B14`** invitation convive annulée → invité (in-app).
20. **`GAP-B7`** don de points gift → bénéficiaire (si feature retenue V1).
21. **`GAP-N5`** iOS Win destination `announcement` dédiée.
22. **`GAP-N9`** iOS Store chips loyalty/promo.
23. **`GAP-N10`** supprimer/brancher `DeviceTokenSyncer` placeholder (Win+Store).
24. **`GAP-N7`/`GAP-N8`** data-push + routing sous-écran via metadata (réintroduire metadata dans le DTO si besoin de fiches précises).

### Règles d'implémentation backend (rappel)
- Pattern : `event` (résolution destinataires côté module source, portée sur l'event — frontière Modulith ; `core.notification` CLOSED itère) → `@ApplicationModuleListener` dans `NotificationEventHandler` → `createInApp` (in-app) et/ou `pushToUser`/`sendReservation`/`sendPromo` (push FCM).
- `hasAuthority` only (jamais `hasRole`/`isAuthenticated`).
- Tests **unit + intégration** obligatoires par lot ; ModularityTests doivent rester verts.
- Réutiliser les types cloche existants (`reservation`/`community`/`loyalty`/`promotion`/`system`/`announcement`/`friend_request`/`seminar`) pour churn natif minimal.
- Pas de microservice, pas d'EF dédiée PCC.

### Décisions à trancher par l'utilisateur
- `GAP-P2` : push sur annonce tenant ? in-app sur campagne promo ?
- `GAP-P1` : appliquer réellement les préférences staff V85 comme filtre ?
- `GAP-B7` : feature gift-points retenue V1 ?
- `GAP-B13` : annonces programmées dans le scope V1 ?
