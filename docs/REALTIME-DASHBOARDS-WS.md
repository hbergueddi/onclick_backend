# Dashboards temps réel via WebSocket/STOMP (Bug 37)

> Contexte : on quitte Supabase Realtime → tout passe sur notre Postgres, exposé
> par Spring. Demande du dev senior : **tous les dashboards à KPI doivent être en
> temps réel via WebSocket**. Ce document fait l'inventaire admin/resto/client,
> liste ce qui est livré, et le punch-list de ce qui reste (avec l'étape pour
> débloquer chaque cas).

## Principe — bridge d'invalidation (pas de métier sur le WS)

Le WS ne transporte **pas** la donnée métier. Il transporte un signal *« ce
domaine a changé »*. Le front réagit en invalidant ses queries React Query, qui
se re-fetchent via leur **chemin REST existant**. Avantages : aucune duplication
de la logique d'agrégat sur 2 transports, sécurité REST inchangée, et un
dashboard se branche en **une ligne**.

```
DB change ──(intervalle/abonnement)──> Publisher Spring ──push /topic/...──> RealtimeProvider (1 client STOMP)
                                                                                   │
                                                            invalidateQueries(queryKeys) ──> re-fetch REST
```

### Ajouter un dashboard = 1 publisher backend + 1 badge front

**Backend** — une sous-classe d'`AbstractDashboardPublisher` :

```java
@Component
public class XxxDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {
    public static final String TOPIC = "/topic/admin/xxx";
    @PersistenceContext private EntityManager em;
    public XxxDashboardPublisher(SimpMessagingTemplate t) { super(t); }
    @Override protected String topic() { return TOPIC; }
    @Override protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "xxx_table");           // table avec updated_at
        // ou : RealtimeFingerprint.of(em, "xxx_table", "created_at");  // sans updated_at
    }
}
```

- La base gère : `@Scheduled` (gate sur sessions actives → 0 requête si personne
  n'écoute), push-si-changé (`equals` sur le snapshot), push immédiat à
  l'abonnement. `onSubscribe`/`scheduledPublish` sont `@Transactional(readOnly)`.
- `RealtimeFingerprint` = `(count(*), max(<colonne de changement>))`. Le nom de
  table ET la colonne sont des **constantes en dur** côté publisher (jamais
  d'input utilisateur → pas d'injection SQL).
- Le module métier doit déclarer `"realtime"` dans `allowedDependencies`
  (`realtime` est un module **OPEN**, infra transverse).
- Autorisation : `StompAuthChannelInterceptor` exige l'authority `SUPERADMIN`
  pour s'abonner à `/topic/admin/**`.

**Front** — une ligne dans la page :

```tsx
<RealtimeDashboardBadge topic="/topic/admin/xxx" queryKeys={[["clef-react-query"]]} />
```

Inactif si `VITE_REALTIME_DASHBOARDS !== "true"`. L'invalidation est par préfixe
(react-query) : `["admin_wallet"]` invalide aussi `["admin_wallet", userId]`.

## Inventaire — dashboards ADMIN

| Dashboard            | Route                    | Source Spring             | Topic                   | Statut |
|----------------------|--------------------------|---------------------------|-------------------------|--------|
| Exec (vue d'ensemble)| `/` (Index)              | `AdminStatsFullDto`       | `/topic/admin/exec`     | ✅ LIVE |
| Parrainage           | `/pulse/parrainage`      | `referrals`               | `/topic/admin/referral` | ✅ LIVE |
| OneClick Lounge      | `/pulse/lounge`          | `loyalty_accounts`        | `/topic/admin/lounge`   | ✅ LIVE |
| Cockpit HI           | `/oneclickhi`            | `oneclick_hi_invoices`    | `/topic/admin/hi-cockpit`| ✅ LIVE |
| Anti-fraude (KPI)    | `/pulse/anti-fraude`     | `system_alerts`           | `/topic/admin/alerts`   | ✅ LIVE |
| Dashboard fraude     | `/trustwatch/fraude`     | `system_alerts`           | `/topic/admin/alerts`   | ✅ LIVE |
| Flux d'alertes       | `/trustwatch/flux`       | `system_alerts`           | `/topic/admin/alerts`   | ✅ LIVE |

**7 pages live sur 5 topics / 5 publishers.** Les 3 écrans fraude partagent un
seul publisher (`SystemAlertsDashboardPublisher`) ; son empreinte combine
création et acquittement (`greatest(created_at, acknowledged_at)`) — acquitter
une alerte rafraîchit aussi les autres écrans ouverts.

## Punch-list — non encore branchés

### A. Dashboards multi-sources (faisables, à empreinte composite) — DIFFÉRÉ

Une empreinte sur une seule table ne capte pas tous leurs changements. Il faut
soit une empreinte composite (somme de plusieurs `(count, max)`), soit plusieurs
topics par page. Reporté pour ne pas alourdir chaque tick avec N requêtes sans
validation préalable.

| Dashboard        | Route                 | Sources Spring                              | Étape pour débloquer |
|------------------|-----------------------|---------------------------------------------|----------------------|
| Admin Wallet     | `/pulse/wallet`       | `wallet_transactions` (soft-delete) + points expirés (`loyalty`) | publisher `wallet` avec `RealtimeFingerprint.of(em, "wallet_transactions", "created_at")` filtré `deleted_at IS NULL` + topic points expirés (loyalty) |
| Cohortes clients | `/pulse/cohortes`     | `reservations` + `users`                    | empreinte composite (réservations + users) sur 1 topic |
| Tableaux Pulse   | `/pulse/tableaux`     | 7 sources (résa, loyalty, users, parrainage, support, restos, alertes) | empreinte composite multi-tables ou réutiliser les topics existants (exec/referral/lounge/alerts) |

### B. Bloqués

| Dashboard        | Route                 | Blocage                                      | Étape pour débloquer |
|------------------|-----------------------|----------------------------------------------|----------------------|
| Support KPI      | `/pulse/support`      | lit encore **Supabase** (read path non migré)| migrer la lecture vers `SupportController` Spring, puis publisher `support` (`support_tickets`) |
| Tickets OCR      | `/pulse/tickets-ocr`  | pas d'endpoint Spring **global** (per-resto seulement) | créer un endpoint d'agrégat OCR admin, puis publisher |
| Dashboards resto / groupe (ProDesk) | `/prodesk/*` | besoin d'un **topic par tenant** `/topic/restaurant/{id}/...` + autorisation d'abonnement vérifiant la propriété ; l'intercepteur ne gère aujourd'hui que `/topic/admin/**` (SUPERADMIN). Certaines pages lisent encore Supabase. | étendre `StompAuthChannelInterceptor` (authz scoping par restaurant) **avant** d'ajouter des publishers per-tenant |

### C. Hors périmètre (décision : skip)

- **System Health** (`/pulse/system`) — métriques infra/heartbeats, pas de la
  donnée métier ; pas pertinent en push permanent.
- **Tech KPI** (`/pulse/tech`) — cartes **statiques** (pas de source live).
- Menus, listes, pages de réglages — pas des dashboards.

## Garde-fous

- **Aucune table sans la bonne colonne de changement** : `RealtimeFingerprint`
  fait `max(updated_at)` par défaut → vérifier que la table l'a, sinon passer la
  colonne explicite (`created_at`, `greatest(...)`). Une mauvaise colonne =
  échec **runtime** (la requête native n'est pas couverte par les tests).
  Tables `TimestampedEntity` → `updated_at` OK ; `CreatedAtEntity` / alertes →
  colonne explicite obligatoire.
- **Coût DB borné** : gate sur sessions actives (0 abonné → 0 requête) +
  intervalle `app.realtime.dashboard.interval-ms` (défaut 30 s).
- **Sécurité** : le WS ne porte aucune donnée métier ; la donnée reste derrière
  REST + RBAC. Abonnement `/topic/admin/**` réservé à `SUPERADMIN`.
