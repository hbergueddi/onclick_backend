# Scope — Alertes pré-expiration (points fidélité + contrats partenaires)

> **Date** : 12 juin 2026. **Statut** : ✅ **IMPLÉMENTÉ & VÉRIFIÉ** (A + B + fix `renewContracts`), non committé.
> Tests : **42/42 verts** dont 1 intégration live (SQL réel + chaîne event→notif + fix renewContracts) + ModularityTests.
> Faits levés à l'implémentation : `auto_renew` est **NOT NULL DEFAULT false** (pas de trou NULL) ; recipient admin = **SUPERADMIN** (1 user) ; le front a déjà un panneau `ContractExpirationAlerts` que cette couche notif (push + in-app) complète.
> **Périmètre** : 100 % `OneClick_Spring`.
> **Origine** : porter les fonctions legacy `notify-expiring-points` / `notify-expiring-contracts` (4click-hh)
> qui **préviennent AVANT** l'échéance. Spring ne fait aujourd'hui que *traiter* l'expiration a posteriori
> (`LoyaltyCronJobs.processExpiredPoints`, `FinancialCronJobs.renewContracts`) — **aucune alerte préventive**.

## Décisions verrouillées (user, 12/06)

| # | Décision | Choix |
|---|----------|-------|
| 1 | Jalons d'alerte (les deux features) | **J-30 / J-15 / J-7** |
| 2 | Quels contrats alerter (feature B) | **Option 1** : uniquement `auto_renew = false` (ceux qui vont *réellement* expirer → l'admin doit agir) |
| 3 | Canaux | **in-app + push FCM** (les deux features) |

## Architecture (commune aux 2 features)

Réutilise **à l'identique** le pattern event-driven déjà livré (R1 rappels résa, R2 promo) :

```
Cron (sélectionne les échéances dues, anti-doublon par jalon)
   └─ publie un event shared.events
        └─ NotificationEventHandler (@ApplicationModuleListener)
             ├─ createInApp(...) avec metadata {kind, id, milestone}  ← anti-doublon
             └─ pushToUser(...)  (FcmPushService, mode stub si app.fcm.* absents)
```

- **Anti-doublon** : metadata jsonb sur la notif + `NOT EXISTS` dans le SELECT du cron → **1 alerte par jalon**
  (même mécanisme que le rappel H-2 : `metadata->>'milestone'`). Pas de migration (champ `metadata` déjà jsonb).
- **Modulith** : signaux cross-module = events (`shared.events`, OPEN). Pas de nouvelle dépendance typée
  loyalty↔notification ni financial↔notification.
- **Pas de `hasAuthority`** à toucher (crons server-side, déclenchés par `@Scheduled`).
- **Tests obligatoires** : unit (cron : sélection + publish ; listener : recipients/push) + 1 intégration par
  feature (seed échéance → cron → notif créée en base).

---

## Feature A — Points fidélité qui expirent → **client**

| Aspect | Détail (schéma réel vérifié) |
|--------|------------------------------|
| **Trigger** | Nouvelle méthode `@Scheduled` (quotidienne) dans `LoyaltyCronJobs`. |
| **Données** | `loyalty_transactions` : `type='earn'`, `expires_at` à J-30/J-15/J-7, **non déjà expirées** (pas de tx `type='expire'` `reason LIKE 'auto:expired:'||t.id`). Joint `loyalty_accounts (account_id → client_id, balance)`. Agrégé par client. |
| **Destinataire** | Le **client** = `loyalty_accounts.client_id`. **Données loyalty → zéro cross-module.** |
| **Jalons** | J-30 / J-15 / J-7 avant `expires_at`. Un compte n'est alerté qu'une fois par jalon. |
| **Anti-doublon** | notif `metadata = {kind:'points_expiring', accountId, milestone:'j30'|'j15'|'j7'}` ; le SELECT exclut les comptes déjà notifiés pour ce jalon. |
| **Notif** | in-app `type='loyalty'` + push FCM. Libellé type « Vos N points expirent le JJ/MM — pensez à les utiliser ». |
| **Event** | `shared/events/PointsExpiringSoonEvent(recipientUserId, points, expiresOn, milestone, occurredAt)`. |
| **Fichiers** | `PointsExpiringSoonEvent` (créé), `LoyaltyCronJobs` (+1 méthode), `NotificationEventHandler` (+1 listener), réutilise un `NotificationService.createReminder`-style (in-app + metadata). |
| **Tests** | unit `LoyaltyCronJobsTest` (publish 1 event/jalon dû), unit listener, intégration (seed earn `expires_at` J-7 → cron → notif `loyalty` avec metadata). |
| **Option** | seuil min de points pour alerter (ex : ignorer < 10 pts) — **non retenu par défaut**, ajoutable. |

---

## Feature B — Contrats partenaires qui expirent → **admins**

⚠️ **Subtilité levée par la décision « option 1 »** (à lire avant de coder) :

`FinancialCronJobs.renewContracts` **auto-renouvelle** (+1 an) **tous** les contrats `status='active'` finissant
sous 30 jours — **et il IGNORE le flag `auto_renew`** (bug latent : il renouvelle même les `auto_renew=false`).

Donc si on alerte les `auto_renew=false` (option 1) **sans corriger `renewContracts`**, on aurait :
« contrat X va expirer (auto_renew=false) » → puis `renewContracts` le renouvelle quand même → **contradiction**.

➡️ **Conséquence : la correction de `renewContracts` n'est PAS un bonus optionnel, elle est REQUISE pour la
cohérence de l'option 1.** Elle est triviale (1 clause `WHERE`).

| Aspect | Détail (table `contracts` : `ends_at`, `status`, `auto_renew`, `restaurant_id`, `contract_number`, `tenant_id`) |
|--------|------------------------------------------------------------------------------------------------------------------|
| **Trigger** | Nouvelle méthode `@Scheduled` (quotidienne) dans `FinancialCronJobs`. |
| **Données** | `contracts` : `deleted_at IS NULL`, `status='active'`, **`auto_renew = false`**, `ends_at` à J-30/J-15/J-7. |
| **Pré-requis (REQUIS)** | Corriger `renewContracts` : ajouter `AND auto_renew = true` à son `WHERE` (sinon il annule l'intérêt de l'alerte). |
| **Destinataire** | Les **admins** (`SUPERADMIN`). Résolution = read-view native `users ⨝ roles WHERE r.code='SUPERADMIN' AND u.deleted_at IS NULL` (≈ 1 admin). Côté notification (read-view délibérée), ou via `UserDirectoryApi.adminUserIds()` à ajouter. |
| **Jalons** | J-30 / J-15 / J-7 avant `ends_at`. 1 alerte par (contrat × jalon). |
| **Anti-doublon** | notif `metadata = {kind:'contract_expiring', contractId, milestone:'j30'|'j15'|'j7'}`. |
| **Notif** | in-app `type='system'` + push FCM, **par admin**. Libellé « Contrat <numéro> (resto X) expire le JJ/MM — sans renouvellement auto ». |
| **Event** | `shared/events/ContractExpiringSoonEvent(contractId, restaurantId, contractNumber, endsAt, milestone, occurredAt)`. |
| **Fichiers** | `ContractExpiringSoonEvent` (créé), `FinancialCronJobs` (+1 méthode cron, +1 ligne fix `renewContracts`), `NotificationEventHandler` (+1 listener + résolution admins), éventuellement `UserDirectoryApi.adminUserIds()`. |
| **Tests** | unit `FinancialCronJobsTest` (sélection auto_renew=false + publish ; + test que `renewContracts` ne touche plus les auto_renew=false), unit listener (résout admins, push chacun), intégration (seed contrat `auto_renew=false` `ends_at` J-7 → cron → notif `system` aux admins). |

---

## Synthèse implémentation

- **Effort** : A = petit (≈ copie de R1). B = petit-moyen (+ résolution admins + fix `renewContracts` 1 ligne + sa décision).
- **Migration SQL** : aucune.
- **Ordre suggéré** : A (autonome) → B (avec le fix `renewContracts` dans le même lot, pour la cohérence).
- **Conformité senior** : event-driven Modulith, unit + intégration par feature, zéro `hasAuthority` impacté.

## Reste à confirmer (mineur, n'empêche pas de démarrer)
- Seuil minimal de points (feature A) : alerter pour tout montant, ou ignorer les très petits soldes ?
- Canal admin (feature B) : push **+** in-app retenu ; si un admin n'a pas de device-token, seul l'in-app part (comportement normal du fan-out).
