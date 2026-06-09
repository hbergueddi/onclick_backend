# Architecture monolithe cible — OneClick (Spring Modulith)

> Réponse actionnable à la mission « transformer des modules en monolithe propre, maintenable,
> scalable ». S'appuie sur l'existant (le projet EST déjà un monolithe Spring Modulith mono-DB) :
> ce document fige les invariants et la stratégie pour ne pas dériver vers un *big ball of mud*.

## 1. Architecture cible (1 déployable, 1 DB)

- **Spring Boot 3 / Java 17**, un seul JAR, une seule base PostgreSQL (`oneclick_enterprise`).
- **Spring Modulith** : les frontières de modules sont **vérifiées au build** (`ApplicationModules.verify()`
  dans `ModularityTests`) — c'est le garde-fou anti-ball-of-mud (échec du build si une dépendance
  illégale apparaît).
- **Temps réel** : WebSocket/STOMP (publishers par tenant) — jamais de polling pour les dashboards.
- **Sécurité** : RBAC v2 par **autorités** `VERB:RESOURCE` (`hasAuthority(...)` exclusivement) +
  ABAC de périmètre (self-scope / membership), jamais `hasRole`/`isAuthenticated`.

## 2. Bounded contexts (regroupement des modules)

Deux familles, séparées physiquement et par dépendances :

- **`core/*` — contextes socles (OPEN, réutilisables)** :
  `identity` (users/roles/permissions), `tenant` (whitelabel), `membership` (appartenances programme),
  `auth`, `email`. Exposent des **ports** (`*.api`) ; cachent leur `internal`.
- **`modules/*` — contextes métier (CLOSED)** :
  `reservation`, `loyalty`, `offer`, `restaurant`, `analytics`, `resource_booking`, `event`, `family`,
  `feedback`, `seminar`, `announcement`, `stories`, `membercircle`, `oneclickhi`, `support`, `media`…
- **Transverses** : `security` (adaptateur), `shared` (events/utilitaires), `audit`, `exception`,
  `realtime` (infra STOMP).

Règle de fusion : on **ne multiplie pas** les modules. Un concept éparpillé (« qui accède à quel
programme ») est **consolidé** dans un seul module (`core/membership`) plutôt que dupliqué — c'est ce
qui réduit la dette, pas l'inverse.

## 3. Structure de package (par module)

```
com.onesley.oneclick.<famille>.<module>
├── api/         # CONTRAT public : DTOs (records), ports (interfaces), events. Seul point d'entrée.
├── internal/    # entité JPA, repository, service, controller, publisher. INVISIBLE des autres modules.
└── package-info.java   # @ApplicationModule(type, id, allowedDependencies = {...})
```

- Controllers et publishers vivent **dans le module qui owne le concept** (ex. `MembershipController`,
  `MembershipKpiPublisher` dans `core/membership`), pas dans un module fourre-tout.
- `allowedDependencies` est une **whitelist explicite** par module → toute nouvelle dépendance est un
  choix conscient, tracé, et vérifié au build.

## 4. Modèle de données unifié (dé-duplication)

- **`tenant_memberships`** = source unique de l'accès programme (remplace le scoping dispersé par
  `users.tenant_id` et la colonne legacy `users.pcc_member_type`).
- **`users.tenant_id`** garde un sens UNIQUE : tenant *home* (oneclick pour les clients après le flip
  P3 ; tenant employeur pour staff/admin). Il ne sert **plus** à décider de l'accès programme.
- Graphe RBAC unique : `roles → permissions → (actions × menus)`. Les memberships pointent un `role`
  programme (`MEMBER`) plié dans les autorités au login.
- Pas de dénormalisation cross-module : les lectures inter-modules passent par des **ports** typés
  (`UserDirectoryApi`, `MembershipDirectoryApi`, `TenantDirectory…`) ou des **read-views SQL natives**
  (pattern P2.c) — jamais d'import d'entité d'un autre module.

## 5. Dépendances entre modules & sens autorisé (refactoring)

- `modules.* → core.*.api` (jamais l'inverse, jamais `internal`).
- `security → core.membership.api` (pliage des autorités) — donc **`membership` ne dépend PAS de
  `security`** : le contexte de sécurité y est lu via l'API Spring (`SecurityContextHolder`) et
  l'éviction de cache se fait par **event** (`MembershipActivatedEvent`), pas par appel direct
  (sinon cycle `membership ↔ security`).
- `core.membership → core.identity.api + core.tenant.api` (lecture/création de comptes, résolution
  tenant) → donc `tenant` et `identity` **ne dépendent pas** de `membership` ; quand `identity` aurait
  besoin des memberships (ex. /me), on **expose un endpoint séparé** porté par `membership`
  (`/api/me/memberships`) plutôt que d'inverser la dépendance.
- Cross-module business↔business **interdit** : `loyalty`/`stories`/… ne s'importent pas ; les lectures
  partagées (staff actif, etc.) passent par read-views SQL natives.
- Découplage temporel : événements `shared` (module OPEN) pour les effets de bord (email, notif,
  éviction cache, push KPI), consommés par `@TransactionalEventListener(AFTER_COMMIT)` /
  `@ApplicationModuleListener`.

## 6. Responsabilités sans casser la séparation

- **Un module owne son concept** de bout en bout (DDL, entité, service, controller, events, publisher).
- Les autres modules le consomment **uniquement** via son `api/`. Si un besoin transverse émerge
  (ex. « le tenant programme du caller »), on l'expose comme **méthode de port** réutilisable
  (`activeTenantIds`) plutôt que de le ré-implémenter dans chaque module.
- ABAC : la décision « cet utilisateur peut-il agir dans ce tenant ? » est centralisée
  (`MembershipDirectoryApi.isActiveMember`), pas recopiée en comparaisons `home-tenant` ad hoc.

## 7. Stratégie de migration (modulaire → monolithe propre) — vécue ici

Réalisée en phases additives, chacune validée par la suite complète (filet de sécurité) :
1. **P0** — nouveau bounded context `core/membership` + table `tenant_memberships` + backfill miroir
   (comportement inchangé).
2. **P1** — pliage des autorités via membership (additif) ; **P1.5** — durcissement chirurgical des
   autorités (découverte vs membre-only).
3. **P2** — invitation admin tenant (réutilise/crée 1 compte OneClick + membership).
4. **P3** — flip identité (clients → home oneclick), révélation par membership, members admin + KPIs
   STOMP ; **et nettoyage immédiat** de tout scoping client par home-tenant cassé par le flip
   (audit dédié → corrections membership/global, validées par la suite).
5. **P4** — finir le nettoyage (lectures résiduelles `tenantIdById` côté membre → `activeTenantIds`,
   suppression `pcc_member_type`), `verify()` vert, suite complète.

Principe : **flip + nettoyage du concern déplacé vont ensemble** (on ne laisse pas deux sources de
vérité coexister), et la **suite complète est l'arbitre** (un changement transverse n'est « fini »
que quand 100 % vert).

## 8. Anti-« big ball of mud » (pratiques non négociables)

- `ApplicationModules.verify()` en test **obligatoire** (échec build si frontière violée).
- `allowedDependencies` explicite par module ; toute extension = ajout conscient + revue.
- Zéro import `internal` cross-module ; lectures via ports / read-views natives.
- `hasAuthority(...)` only ; ABAC centralisé ; pas de logique de sécurité recopiée.
- Effets de bord par events (pas d'appels synchrones cross-module pour notif/email/cache/push).
- Tests **unitaires isolés + intégration** à chaque changement backend ; mocks pour l'externe.
- Dashboards temps réel = WebSocket/STOMP ; polling seulement sur contrainte documentée.
- Migrations Flyway append-only, tracées en git, jamais éditées après application.
