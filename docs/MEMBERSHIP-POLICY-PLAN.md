# Politique de comptes — OneClick unique + memberships additives (multi‑tenant)

> Statut : **EN COURS (P0)** · Validé 09/06/2026 · Périmètre : `OneClick_Spring`, `OneClick_Spring_FrontEnd`, `android-native-reconstruction` **uniquement** (jamais la prod / `4Click` / `4click-hh`).

## 1. Objectif
Un client n'a **qu'un seul compte OneClick** (son identité + son accès à l'app). L'accès au contenu d'un **programme** (PCC, HOMU, futurs) est une **capacité additive** : l'**admin du tenant** invite le client depuis `tenant/{slug}/`, ce qui crée une **membership** octroyant les **autorités** d'accès au programme. Pas de 2ᵉ compte, pas de changement d'identité.

## 2. Invariants
1. 1 personne = 1 compte OneClick (tenant *home* = `oneclick`). L'invitation réutilise le compte existant ou le crée, puis ajoute une membership.
2. La membership est un concept **client**. Le **staff/admin tenant** reste rattaché à son **tenant employeur** (géré côté Store). 
3. **2 apps** : OneClick **Win** (client — révèle les espaces programme selon memberships, thème contextuel) + OneClick **Store** (staff — gestion par tenant). Plus d'app brandée séparée.
4. Générique : `tenant_memberships` supporte tout programme (aucun hardcode tenant).

## 3. Modèle d'autorisation — `hasAuthority(...)` uniquement
- **Interdit** : `isAuthenticated`, `hasRole`, tout contrôle basé rôle.
- `tenant_memberships.role_id` → rôle programme (ex. `PCC_MEMBER`) → permissions.
- Authorities effectives = `base.role.permissions ∪ ⋃(membership active → role.permissions)` (chargées dans `OneClickUserDetailsService`, cache évincé sur changement de membership).
- Non‑membre → pas l'autorité programme → **403**.
- **ABAC périmètre** (un membre PCC ≠ accès HOMU) : `MembershipDirectoryApi.isActiveMember(userId, resourceTenantId)`.

## 4. Architecture (monolith Spring Modulith — 1 DB, 1 déployable, pas de microservices)
Nouveau **bounded context** `modules/membership` + **consolidation** du scoping programme (aujourd'hui éparpillé) derrière **un port** → réduit le *big ball of mud*.

```
modules/membership/
  api/        MembershipDirectoryApi (port), DTOs (records), events
  internal/
    domain/        TenantMembership (entity)
    repository/    MembershipRepository
    service/       MembershipService
    controller/    MembershipController        (@PreAuthorize hasAuthority(...))
    MembershipKpiPublisher (STOMP)
```

Dépendances **à sens unique** (Modulith CLOSED, garde‑fou `ApplicationModules.verify()`) :
- `identity/security` → `membership.api` (pliage authorities).
- modules programme (`resource_booking`, `family`, `feedback`, `event`, `announcement`) → `membership.api` (ABAC périmètre), **en remplacement** des scopings `userDirectory.tenantIdById(...)` dispersés.
- `membership` → `identity` + `tenant` via ports existants (jamais d'import `internal`).

**Modèle de données unifié** : `tenant_memberships` = source unique de l'accès programme. `users.pcc_member_type` migre vers `tenant_memberships.member_type`. `users.tenant_id` = tenant *home* (sens unique).

### Schéma `tenant_memberships`
| colonne | type | note |
|---|---|---|
| id | uuid PK | |
| user_id | uuid NOT NULL → users | |
| tenant_id | uuid NOT NULL → tenants | programme |
| member_type | varchar(32) | resident / non_resident / null |
| role_id | uuid → roles (nullable) | rôle programme (utilisé P1) |
| status | varchar(32) NOT NULL | invited / active / revoked |
| invited_by | uuid → users (nullable) | admin tenant |
| joined_at | timestamptz (nullable) | |
| created_at / updated_at | timestamptz | |
| deleted_at | timestamptz (nullable) | soft delete |
| UNIQUE (user_id, tenant_id) | | 1 membership / user / programme |

## 5. Roadmap (chaque lot backend = tests **unit isolés + intégration**)
- **P0 — Fondation (additif, iso‑comportement)** : ✅ **FAIT** (`2a1ec1f`). Flyway `V90__tenant_memberships.sql` + module `core/membership` + port `MembershipDirectoryApi` + **backfill miroir** (chaque CLIENT palmeraie/homu → membership active, `member_type` repris ; sélection par **slug**, pas d'UUID ; 83 memberships). *Tests* : unit `MembershipService` + intégration backfill + `ModularityTests`.
- **P1 — Pliage des authorities via membership (ADDITIF)** : ✅ **FAIT** (`b926688`). `V91` rôle générique **MEMBER** + copie data‑driven des perms programme + `tenant_memberships.role_id`. `authoritiesFor(userId)` plié dans `OneClickUserDetails.programAuthorities` au load (éviction cache sur changement). `V92` corrective (revert du retrait trop large). *Tests* : unit (pliage) + intégration (membre obtient les autorités, non‑membre vide). **`/me.memberships[]` reporté en P3** (concern front).
- **P1.5 — Durcissement SURGICAL du CLIENT** : ✅ **FAIT** (`c395a41`). `V93` retire du CLIENT global le **membre‑only** (`FAMILY`/`BOOKINGS`/`FEEDBACK`/`EVENT_RSVP` + `SEMINARS` non‑VIEW), GARDE la **découverte** (`VIEW:RESOURCE_BOOKINGS`/`VIEW:EVENTS`/`VIEW:SEMINARS`) → vrai gate **non‑membre → 403**. *Tests* : `MembershipAuthority` (+gate), `ResourceBookingRbac` (acteur membre + non‑membre 403), `EventFlow` (RSVP membre). ⚠️ flush cache `userDetails` après tout changement de matrice RBAC.
- **P2 — Invitation admin tenant** : ✅ **FAIT** (`1ede991`). `POST /api/tenants/{tenantId}/members` `@PreAuthorize('CREATE:MEMBERSHIPS')` (V94 : ressource MEMBERSHIPS, grant RESTAURATEUR/GROUP_ADMIN/SUPERADMIN — les admins PCC/HOMU sont RESTAURATEUR home=programme). `MembershipInviteService` : réutilise/crée le compte OneClick (home=oneclick) + crée/réactive membership active (MEMBER), idempotent. ABAC own-tenant (bypass SUPERADMIN via `DELETE:TENANTS`). Email brandé via `MemberEnrollmentRequestedEvent` (réutilisé). Éviction cache via `MembershipActivatedEvent` → listener `security` (anti-cycle). *Tests* : unit (4) + listener (2) + intégration (6 : admin 201 / SUPERADMIN tout tenant / réutilisation+idempotent / CLIENT 403 / cross-tenant 403 / 401) + Modularity.
- **P3 — Flip identité + login + révélation** (FrontEnd + natif) : migration clients base → `oneclick` ; login accepte tout client ; Win révèle espaces programme + thème contextuel ; page **Membres** (tenant/{slug}) + **KPIs membres en STOMP** (`MembershipKpiPublisher`).
- **P4 — Nettoyage** : suppression filtre tenant client mort + lectures `pcc_member_type` ; `verify()` vert ; suite complète.

## 6. Checklist de livraison (non négociable)
1. Dashboards temps réel → **WebSocket/STOMP** (jamais de polling sauf contrainte documentée).
2. Sécurité → **`hasAuthority(...)`** uniquement.
3. **Tests unitaires isolés** présents.
4. **Tests d'intégration** présents.
