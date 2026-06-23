# Plan — Inscription restaurant (demande de partenariat → validation admin → compte avec mot de passe temporaire)

> **Source de vérité de ce chantier.** À suivre à la lettre, mis à jour à **chaque fin de lot**.
> Créé le 22/06/2026. Périmètre verrouillé : `OneClick_Spring` (backend) + `OneClick_iOS_Native` (Store) + `android-native-reconstruction` (Store) + `OneClick_Spring_FrontEnd` (admin web). JAMAIS toucher `4click-hh` / prod.

## Objectif (spec user)
1. Le restaurateur remplit un **formulaire 4 étapes** (comportement legacy `4click-hh` `src/pages/store/StoreOnboarding.tsx`, **avec autocomplete Google Places**).
2. Il accepte les **CGU** et envoie sa **demande d'inscription**.
3. Un **email** part **à lui** ET **à `contact@onesley.com`** (templates HTML legacy remis au goût du jour — nouveau logo/design Onesley).
4. Côté **admin** : on **étudie** la demande et on **valide / refuse**.
5. Si **validée** → email au futur restaurateur avec un **mot de passe temporaire**.
6. **À la 1re connexion**, il doit **saisir son mot de passe** (changement forcé).

## Décisions verrouillées (défauts recommandés)
- **Livraison identifiant** : mot de passe temporaire (aléatoire, BCrypt) + flag `password_must_change` (PAS `credentialsNonExpired=false` qui bloquerait le login).
- **Destinataire interne** : `contact@onesley.com` (recipient). **From** reste `OneClick <noreply@app-oneclick.net>` (domaine Resend vérifié — pas de DNS à faire).
- **À l'approbation** : provisionne **restaurant + compte owner + restaurant_staff(owner)** + mdp temporaire.
- **Revue admin** : web `OneClick_Spring_FrontEnd` (forge), pas d'écran admin natif.
- **Formulaire public** : iOS Store + Android Store natifs (+ endpoint public réutilisable).
- **Statuts** : `pending` (défaut) → `approved` / `rejected` (aligné sur l'existant Spring `OnboardingDecisionDto`).

## Directives backend (non négociables — rappel)
- `hasAuthority(...)` UNIQUEMENT (jamais `hasRole`/`isAuthenticated`). Endpoint de soumission = **public** (permitAll, candidat non authentifié).
- Tests **unitaires (Mockito) + intégration** à CHAQUE lot backend.
- WebSocket pour le temps réel (file admin).
- Monolithe / DB unique / Modulith (events `shared/events` + listeners `core.email`/`core.notification`, zéro lecture cross-module).

## Existant Spring réutilisé (NE PAS reconstruire)
- `modules/store/StoreOnboardingController` : `POST /api/store/onboarding` (public), `GET` + `/{id}` (`VIEW:TENANTS`), `PATCH /{id}/decision` (`UPDATE:TENANTS`).
- `modules/store/internal/StoreOnboardingService` + `StoreOnboardingRequest` (entité) + `StoreOnboardingRepository`.
- DTOs `StoreOnboardingDtos` (OnboardingCreateDto a déjà tous les champs wizard : restaurantName, cuisine, city, address, phone, owner*, budget, description, ownerRole, ice, ifNumber, rc, patente, capacity, services[]).
- Events `StoreOnboardingRequestedEvent` (→ notif in-app admin via `NotificationEventHandler`) + `StoreOnboardingDecidedEvent` (→ `StoreOnboardingDecisionEmailListener` Resend, email verdict + lien login).
- `core/email` : `ResendClient` (from défaut `OneClick <noreply@app-oneclick.net>`, suppression-list, stub-safe, brand per-tenant), `EmailTemplateService` (rendu `{{var}}`).
- Migrations existantes : V22 (telemetry), V58 (enrollment fields), V77 (restaurant_onboarding).
- `User.credentialsNonExpired`, `UserService.changePassword` + `POST /api/users/me/password`, `MeContextDto`.
- `GooglePlacesEnrichmentService` (serveur, enrichissement — base pour le proxy autocomplete).

## Manques identifiés (= périmètre à construire)
1. Aucun email à la **soumission** (applicant + contact@onesley) — seulement notif in-app.
2. L'**approbation ne provisionne RIEN** (pas de resto, pas de compte, pas de mdp temporaire).
3. Pas de **mdp temporaire** ni de **changement forcé 1er login**.
4. Pas d'endpoint **Google Places autocomplete** public.
5. **Formulaires natifs** iOS/Android inexistants.
6. **Templates email** à rebrander (nouveau logo Onesley).
7. `contact@onesley.com` absent partout.

---

## LOTS (checklist — statut mis à jour à chaque fin de lot)

Légende : [ ] à faire · [~] en cours · [x] fait+vérifié

### Parenthèse — AASA Associated Domains (HORS périmètre onboarding, demandé en préalable)
- [x] AASA en ligne (200, JSON, 2 App IDs) + entitlement Win présent + **ajouté au Store** + **build device signé VERT** (Win + Store) → capability active sur les 2 App IDs. (22/06)

### Lot BE-1 — Emails de soumission (applicant + contact@onesley) ✅ LIVRÉ+TESTÉ (22/06)
- [x] Enrichi `StoreOnboardingRequestedEvent` (+ownerEmail, ownerFirstName, city).
- [x] `core/email/internal/StoreOnboardingRequestEmailListener` : 2 envois Resend (interne `contact@onesley.com` toujours + accusé applicant si email) ; config `@Value app.email.internal.onboarding` défaut contact@onesley.com (override env `APP_EMAIL_INTERNAL_ONBOARDING`).
- [x] 2 templates `email-templates/default/store-onboarding-request-{internal,received}.html` (minimal, rebrand BE-6).
- [x] Notif in-app admin existante conservée (event enrichi, accessors inchangés).
- [x] Tests : `StoreOnboardingRequestEmailListenerTest` (2, unit Mockito) + intégration `StoreOnboardingNotificationFlowIntegrationTest` (3, event enrichi) + régression décision(4)/service(8). **17 tests verts, BUILD SUCCESS.**

### Lot BE-2 — Approbation = provisioning + mot de passe temporaire ✅ LIVRÉ+TESTÉ (22/06)
- [x] **Ports cross-module** (archi « via api packages or events ») :
  - `core.identity.api.UserProvisioningApi.provisionOwner(cmd)` (impl `UserProvisioningService`) — crée le compte rôle RESTAURATEUR + mdp temporaire BCrypt + `password_must_change=true` (réutilise `UserService.create` → gardes conflit email/tél).
  - `modules.restaurant.api.RestaurantProvisioningApi.provisionWithOwner(cmd)` (impl `RestaurantProvisioningService`) — crée resto (`RestaurantCatalogService.create`) + `restaurant_staffs(owner)` (`addStaff`). `modules/restaurant/api` exposé en **`@NamedInterface("api")`** ; `store` déclare `"modules.restaurant :: api"`.
- [x] `StoreOnboardingService.decide()` branche `approved` **synchrone** (admin reçoit 409 immédiat si email pris ; email envoyé seulement après succès) : tenant résolu (`tenantId` demande || tenant public `oneclick` via `TenantDirectoryApi`), mdp temporaire généré (SecureRandom 12c), provision user → resto+staff, persiste `provisioned_user_id`/`provisioned_restaurant_id`. **Idempotent** : ré-approbation d'une demande déjà provisionnée = no-op.
- [x] `StoreOnboardingDecidedEvent` enrichi (`tempPassword`) + `LoginResult`/`LoginResponseDto` inchangés ; listener email insère `loginEmail`+`tempPassword` dans le template `approved` (bloc identifiants).
- [x] V106 `store_onboarding_requests.provisioned_user_id`/`provisioned_restaurant_id` (audit + ancre idempotence) + DTO `OnboardingRequestDto` enrichi.
- [x] Tests : unit `UserProvisioningServiceTest` (2) + `RestaurantProvisioningServiceTest` (1) + `StoreOnboardingServiceTest` (decide approved/rejected/idempotent, 9) + intégration **E2E `StoreOnboardingApprovalProvisioningIntegrationTest`** (approve → user RESTAURATEUR+flag + resto + staff(owner) + login mdp temp → `passwordMustChange=true` → change → re-login `false`) + régression flow/notification (cleanup soft-delete). **ModularityTests vert, BUILD SUCCESS.**
- **Done** : 62 tests verts + Modulith vert.

### Lot BE-3 — Premier login = saisie obligatoire du mot de passe ✅ LIVRÉ+TESTÉ (22/06)
- [x] Migration `V105__users_password_must_change.sql` (`ADD COLUMN IF NOT EXISTS … DEFAULT false`) — appliquée à oneclick_enterprise (vérif via Hibernate SELECT au boot du test d'intégration).
- [x] `User.passwordMustChange` (col + `@Setter`, défaut false) — drapeau dédié, distinct de `credentialsNonExpired` (qui bloquerait le login).
- [x] Exposé dans **réponse login** (`AuthService.LoginResult` + `AuthController.LoginResponseDto`, 4 sites de construction) ET **`MeContextDto.UserSummary`** (build `UserService.findMeContext`).
- [x] `changePassword` **et** `resetPassword` réussis → `passwordMustChange=false` + évict cache (cohérence Phase A mot de passe oublié).
- [x] Tests : 3 unit (`UserServiceTest` : change clear / reset clear / MeContext expose) + régression login/reset intégration. **43 tests verts, BUILD SUCCESS.**
- **Done** : tests verts ; le flux E2E « login mdp temp → flag true → change → flag false » sera couvert par l'intégration de BE-2 (qui crée le 1er compte avec flag true).

### Lot BE-4 — Google Places autocomplete public ✅ LIVRÉ+TESTÉ (22/06)
- [x] `GET /api/places/search?q=&country=Maroc&limit=` **public** (`PlacesController`, permitAll) + rate-limit Bucket4j (`/api/places/search` 20/min/IP) ; réponse sanitizée `PlaceSuggestionDto{displayName, formattedAddress, nationalPhoneNumber, city, cuisine}`.
- [x] `GooglePlacesEnrichmentService.searchSuggestions()` (searchText Google v1, langue FR, FieldMask minimal) — **stub-safe** (liste vide si clé absente/erreur) ; mapping ville (`locality`→`admin_area_2`) + cuisine FR (réutilise `TYPE_TO_CUISINE`).
- [x] Tests : unit `GooglePlacesSearchMappingTest` (stub + mapping ville/cuisine, 3) + intégration `PlacesSearchIntegrationTest` (public 200 array + 400 sans `q`, 2). **7 tests verts + Modulith.**

### Lot BE-5 — WebSocket file admin ✅ LIVRÉ+TESTÉ (22/06)
- [x] `StoreOnboardingPublisher` (modules.store.internal) → STOMP `/topic/admin/onboarding` ; appelé par `create()` après save (best-effort, `SimpMessagingTemplate` = bean framework, pas de dép Modulith). Pattern `FeedbackPublisher`.
- [x] Tests : `StoreOnboardingPublisherTest` (push topic + best-effort swallow, 2) + verify dans `StoreOnboardingServiceTest`. **16 tests verts + Modulith.**

### Lot BE-6 — Templates email rebrandés Onesley ✅ LIVRÉ (22/06)
- [x] Les 2 templates de décision (`store-onboarding-decision-approved/rejected`) étaient déjà sur la charte plum Onesley (`#714B67` + crème `#f4f1ee`).
- [x] Rebrand des 2 templates de soumission (`store-onboarding-request-internal/received`) : abandon de l'ancien header sombre `#111827` → charte plum (carte crème, headings plum, footer « OneClick — Fidélité, réservations et offres. »). Tables 100% / escapeHtml `{{var}}` inchangés.

### Lot BE-7 — Vérif backend globale ✅ LIVRÉ (22/06)
- [x] **Suite Spring complète exécutée** : 2963 tests, **seulement 4 rouges** — et ces 4 sont du **WIP co-dev NON committé**, hors périmètre onboarding : `MediaScanRbacIntegrationTest`, `ResourceBookingResourceMgmtRbacIntegrationTest` (×2), `ResourceBookingNoShowStatsIntegrationTest`. Preuve : (a) `git status` → ces fichiers + leur SUT (`modules/resource_booking/*`, `core/media`, `AbstractIntegrationTest`, `V101`) sont `M`/`??` d'un autre chantier (push-notif/tests natifs), JAMAIS touchés par ce lot ; (b) ils échouent **en isolation** (dépendance seed `bearerForRole` → 0 user de rôle, drift de `oneclick_enterprise`), donc ni régression ni pollution de mes tests ; (c) mes V105/V106 n'ajoutent que des colonnes (aucun impact resource_booking/media).
- [x] **Tout le delta onboarding est VERT** (BE-1..BE-6 : ~80 tests dédiés inclus dans les 2959 passants) + **ModularityTests vert**.
- [x] `grep hasRole/isAuthenticated` = **0** sur tous les fichiers du delta.
- [ ] **OpenAPI régénéré — DIFFÉRÉ** : `openapi-spec.json` se génère depuis l'app live (`/v3/api-docs`) ; le backend IntelliJ tourne avec l'**ancien** code (je ne dois pas le relancer). À régénérer par l'utilisateur après redémarrage du backend (sinon le spec n'aurait pas les nouveaux endpoints).

### Lot iOS — wizard 4 étapes (OneClick Business / Store) ✅ LIVRÉ+TESTÉ (22/06, via workflow + vérif indépendante)
- [x] Data layer `OneClickStore/Features/store/onboarding/data/*` : OnboardingDTOs (miroir Spring, vérifié), OnboardingRepository (POST public sans Bearer), PlacesRepository (GET /api/places/search, stub-safe []), câblés dans StoreContainer.
- [x] Wizard SwiftUI 4 étapes + OnboardingViewModel @Observable (port 1:1 legacy : progress+pills, CITIES/CUISINES/ROLES/SERVICES, Places debounce, chips, CGU, écran succès) — charte Store réutilisée.
- [x] Entrée « Inscrire mon restaurant » (fullScreenCover) dans StoreLoginView (mode welcome).
- [x] Gate 1er login : `passwordMustChange` câblé bout-en-bout (MeContext/SessionManager/AuthRepository/AuthDTOs) → ForcePasswordChangeView bloquante → POST /api/users/me/password → ré-hydrate (flag retombe, bascule auto vers shell).
- [x] **Build VERT** : `xcodebuild -scheme OneClickStore` BUILD SUCCEEDED (+ non-régression OneClickWin OK) ; **24 tests** VM/domaine verts. (2 échecs Store pré-existants sans rapport — push routing/PCC.)
- [x] **Vérif indépendante** : noms de champs DTO + endpoints conformes au contrat Spring (grep).

### Lot Android — wizard 4 étapes (Store) ✅ LIVRÉ+TESTÉ (22/06, via workflow + vérif indépendante)
- [x] Data layer `feature/store_onboarding/data/*` : OnboardingCreateDto (@Json restaurantName/ownerEmail/… vérifiés) + OnboardingRequestDto + PlaceSuggestion + StoreOnboardingApi (@POST api/store/onboarding public) + PlacesApi + Repositories + module Hilt.
- [x] Fragment 4 étapes + StoreOnboardingViewModel (Places autocomplete + validation alignée Bean Validation + soumission), layouts + strings.
- [x] Gate 1er login : SetPasswordGateFragment/ViewModel + MeContext/SessionManager/AuthRepository + StoreMainActivity (passwordMustChange → POST /api/users/me/password).
- [x] **Build VERT** : `./gradlew :app:assembleStoreDebug` + `:app:testStoreDebugUnitTest` BUILD SUCCESSFUL ; **1727 tests, 0 échec** (dont +16 nouveaux).

### Lot Admin web (OneClick_Spring_FrontEnd) ✅ LIVRÉ+TESTÉ (22/06, via workflow + vérif indépendante)
- [x] `StoreOnboarding.tsx` : **correctif sécurité** — supprimé l'appel Google direct + clé API hardcodée côté client → recâblé sur GET /api/places/search (réponse plate sanitizée). POST /api/store/onboarding camelCase conforme.
- [x] `forge/DemandesInscription.tsx` : liste + détail + PATCH decision déjà alignés ; **ajout WS LIVE** `<RealtimeDashboardBadge topic="/topic/admin/onboarding">` (nouvelle demande en direct) — vérifié présent.
- [x] **Build VERT** : `tsc --noEmit` exit 0 (re-vérifié indépendamment) + vitest 1807 tests, 0 échec.
- [~] Gate 1er login : **N/A sur l'admin web** (le restaurateur se connecte via les apps Store natives iOS/Android, déjà gatées ; l'admin web = réviseurs SUPERADMIN). `passwordMustChange` n'est consommé nulle part dans le FE admin ; l'ajouter toucherait `AuthContext.tsx` (WIP co-dev actif). **Suivi** si un jour les restaurateurs se connectent aussi au FE web.

### Lot FINAL — vérif E2E + livraison ✅ (22/06)
- [x] Backend : suite Spring complète (delta onboarding 100% vert, Modulith vert) ; clients : iOS build+24 tests, Android build+1727 tests, Web tsc+1807 tests — tous verts. Vérif indépendante du contrat (0 drift) + WS badge + clé Google supprimée.
- [x] MAJ mémoire faite. **Pas de commit** (conforme : aucun commit sans demande ; WIP cohérent avec le reste du repo non committé).
- Rappels résiduels (non bloquants) : OpenAPI à régénérer après redémarrage backend IntelliJ ; gate web si besoin futur.

---

## Journal de mise à jour
- 22/06 — Plan créé. Parenthèse AASA bouclée+testée (Win+Store device-signed verts ; entitlement Store ajouté).
- 22/06 — **BE-1 LIVRÉ+TESTÉ** : emails soumission (interne contact@onesley + accusé gérant) via Resend, event enrichi, 2 templates, 17 tests verts.
- 22/06 — **BE-3 LIVRÉ+TESTÉ** (réordonné avant BE-2 car prérequis) : colonne `password_must_change` (V105 appliquée), drapeau exposé login+MeContext, clear sur change/reset, 43 tests verts.
- 22/06 — **BE-2 LIVRÉ+TESTÉ** : provisioning synchrone à l'approbation (2 ports cross-module identity+restaurant via `@NamedInterface("api")`, mdp temporaire, V106, email avec identifiants), E2E complet vert + Modulith vert.
- 22/06 — **BE-4 LIVRÉ+TESTÉ** (Google Places autocomplete public + rate-limit), **BE-5 LIVRÉ+TESTÉ** (STOMP /topic/admin/onboarding), **BE-6 LIVRÉ** (templates email rebrandés charte plum).
- 22/06 — **BE-7 LIVRÉ** : suite Spring complète = 2963 tests, 4 rouges = WIP co-dev hors périmètre (resource_booking + media RBAC, seed-drift, échouent en isolation, jamais touchés ici). Tout le delta onboarding VERT + Modulith vert + grep hasRole 0. OpenAPI régén différée (backend IntelliJ pas relançable). **→ BACKEND ENTIÈREMENT LIVRÉ.**
- 22/06 — **Lots clients lancés en parallèle** (workflow `onboarding-clients`) : iOS Store wizard + Android Store wizard + Admin web (alignement contrat DemandesInscription/StoreOnboarding + WS live). En cours.
