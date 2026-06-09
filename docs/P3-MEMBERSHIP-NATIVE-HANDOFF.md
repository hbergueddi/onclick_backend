# P3.6 — Membership natif (android-native) : différé avec rationale + plan

> **Statut : non implémenté, différé sciemment.** Le backend (P2/P3) et le web (P3 FE) couvrent le
> modèle « 1 compte OneClick + memberships additives ». Le client natif est laissé pour un lot dédié.

## Pourquoi différé (décision senior)
`android-native-reconstruction` est en **reconstruction 1:1 du legacy iOS, vue par vue** (objectif :
parité stricte avec les écrans existants). Greffer une **feature nouveau-modèle** (révélation par
membership + invitation), **absente du legacy**, au milieu de cette reconstruction :
- jure avec l'invariant « 1:1 legacy » du repo (on ajouterait un écran/flux qui n'existe pas côté
  référence) ;
- risque une demi-implémentation non testée si insérée à chaud.

→ À faire **une fois la reconstruction stabilisée**, comme lot natif explicite.

## Contrat backend prêt (déjà committé, `OneClick_Spring` / `4click_spring`)
- `GET /api/me/memberships` → `MembershipView[]` `{tenantId, tenantSlug, tenantName, memberType, status}`
  (self-scoped, autorité `VIEW:PROFILE`). **Source du reveal natif.**
- `GET /api/tenants/{tenantId}/members` + `POST /api/tenants/{tenantId}/members` + `GET .../members/kpis`.
- STOMP `/topic/admin/membership-kpis/{tenantId}` (KPIs membres temps réel).

## Plan d'implémentation natif (quand le moment est venu)
**Win (client)** — révélation des espaces programme :
1. `MembershipApi` (Retrofit) : `@GET("api/me/memberships")` → `List<MembershipDto>` (Moshi, mêmes
   champs que le contrat). Provider Hilt comme les autres `*Api`.
2. `MembershipRepository` (online-first, `ApiResult`) exposant `activeProgramSlugs()` /
   `isMemberOf(slug)` (mirror FE `useIsMemberOf`).
3. Gate de révélation : afficher l'entrée « espace PCC/HOMU » (nav/profil) **uniquement si**
   `isMemberOf(slug)` — par membership, **jamais** par `users.tenant_id` (devenu oneclick après le
   flip V95). Réutiliser le ViewModel de Home/nav qui compose déjà les sources.

**Store (staff/admin tenant)** — gestion des membres :
4. Écran « Membres » (`prodesk_*` style) : liste (`GET /members`) + invitation (`POST /members`,
   body `{email, firstName, lastName, phone?, memberType?}`) + KPIs live via le `StompClient` natif
   existant (`RealtimeManager`, topic `/topic/admin/membership-kpis/{tenantId}`) — **pas de polling**.
   Gating par autorité `CREATE/VIEW:MEMBERSHIPS` (déjà dans `/me/context` permissions).

**Tests** : `MembershipRepositoryTest` (MockWebServer + Retrofit/Moshi réel) + VM tests (Mockito +
InstantExecutorRule), pattern identique aux modules natifs existants (cf `LoyaltyRepositoryTest`).
