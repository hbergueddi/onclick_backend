# P3.6 — Membership natif (android-native Win) : data layer LIVRÉE, reveal UI en attente

> **Statut : data layer implémentée + compilée + testée** (client Win, repo
> `android-native-reconstruction`, **Java pur, PAS un repo git** → fichiers sur disque, pas de commit).
> Le **branchement UI du reveal** attend la reconstruction des vues programme (PCC).

## Livré (mirror du module `feature/profile` — GET self-scoped, sans cache)
Sous `app/src/main/java/ma/oneclick/feature/membership/` :
- `data/dto/MembershipDto.java` (Moshi, camelCase : tenantId, tenantSlug, tenantName, memberType, status).
- `domain/Membership.java` (slug = clé reveal ; `active` dérivé de `status=='active'` ; `isSlug()` insensible casse).
- `data/MembershipMapper.java` (DTO→domaine, null-safe).
- `data/api/MembershipApi.java` : `@GET("api/me/memberships") Call<List<MembershipDto>>`.
- `data/di/MembershipApiModule.java` (Hilt `@Provides` sur `@DefaultRetrofit`).
- `data/MembershipRepository.java` : `activeMemberships()`, `activeProgramSlugs()`, `isMemberOf(slug)`.
  Online-first, **fail-closed** (false/vide si erreur réseau → aucun espace révélé sans confirmation serveur).
- `test/.../MembershipRepositoryTest.java` — **9 tests verts** (Retrofit/OkHttp/Moshi réel + MockWebServer).

## Build (⚠️ contraintes repo)
- **Java pur** (pas de Kotlin) → tâche = `:app:compileWinDebugJavaWithJavac` (pas `compileWinDebugKotlin`).
- **JDK 26 NE build PAS** ce projet (AGP `JdkImageTransform`/jlink échoue) → utiliser la **JBR 21 d'Android
  Studio** : `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Vérifié : `:app:compileWinDebugJavaWithJavac` + `:app:testWinDebugUnitTest --tests '*MembershipRepositoryTest*'`
  = BUILD SUCCESSFUL (9/9).

## Reste à faire (quand la reconstruction câblera l'entrée PCC)
- **Reveal UI** : aucun point d'entrée programme aujourd'hui (`client/` vide ; `MainActivity` sans gating ;
  `nav_graph_pcc.xml` existe mais startDestination « si tenant=palmeraie » NON câblé, deep-link only).
  Quand la vue PCC est reconstruite : gater son entrée par `MembershipRepository.isMemberOf("palmeraie")`
  **au lieu de** `MeContext.getTenantSlug()` (= `oneclick` pour tous les membres après le flip V95).
- **Store (staff invite)** : le repo `android-native-reconstruction` est **Win-only** (le flavor Store n'y est
  pas câblé). L'écran « Membres » staff (POST /api/tenants/{id}/members + KPIs STOMP) sera à faire dans
  l'app Store quand son flavor existera.

## Contrat backend (live, OneClick_Spring 4click_spring)
`GET /api/me/memberships` → `MembershipView[]` ; `GET|POST /api/tenants/{id}/members` ;
`GET /api/tenants/{id}/members/kpis` ; STOMP `/topic/admin/membership-kpis/{id}`.
