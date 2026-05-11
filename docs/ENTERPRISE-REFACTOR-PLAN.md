# Plan refonte Enterprise Architecture — OneClick Backend

> **Source** : `docs/oneclick_architecture_enterprise_optimized.md`
> **Branche** : `enterprise-architecture` (depuis `main`, archive `archive-phase11-12-20260511`)
> **Cible** : Spring Boot 4.0.6 / Java 26 / Spring Modulith 2.0.6
> **DB** : nouvelle DB `oneclick_enterprise` (la DB `oneclick_local` reste intacte pour ETL)
> **Règle absolue** : 0 push remote, 0 VPS, 0 touche au projet OneClick original

---

## Stratégie

**Approche greenfield modulaire** :
- Schéma cible : ~60 tables (vs ~95 actuelles) en 12 modules métier + 7 sous-modules `core/`
- Architecture Spring Modulith : isolation par bounded context, events inter-modules, plus de `@ManyToMany` sauvages
- Flyway V1+ propre — pas de migration partir-du-legacy, on construit la cible
- ETL data legacy → enterprise dans une phase finale dédiée

## Périmètre — ce qui est gardé / supprimé

### Gardé (patterns réutilisables, indépendants du schéma)
- `OneClickSpringApplication.java` (entry point)
- `audit/` (hierarchy `CreatedAtEntity` / `TimestampedEntity` / `AuditedEntity` — pattern senior)
- `exception/` (GlobalExceptionHandler, ApiException, NotFoundException, etc.)
- `security/` (SecurityConfig, JwtConfig, UserRoleAuthoritiesConverter — adapté au nouveau RBAC)
- `search/` (SearchRequest, SpecificationBuilder)
- `pom.xml` (avec ajout Spring Modulith 2.0.6 + autres deps cibles)
- `application.yml` / `application-dev.yml` (refacto pour pointer nouvelle DB)

### Supprimé (ancien schéma, à régénérer)
- `entity/*` — toutes les entités JPA (33+)
- `dto/*` — tous les DTOs
- `repository/*` — tous les repositories
- `service/*` — tous les services
- `controller/*` — tous les contrôleurs
- `mapper/*` — tous les mappers MapStruct
- `event/*` — events legacy
- `permission/*` — module permissions legacy (remplacé par nouveau RBAC core/identity)
- `db/migration/V1+__*.sql` — toutes les migrations Flyway (replay greenfield)

### Renommage / restructuration
- Plus de structure `entity/auth/` `entity/restaurant/` `entity/loyalty/` à plat
- Nouvelle structure : `core/<sub>/...` + `modules/<module>/...`

## Structure cible des packages

```
com.onesley.oneclick/
├── OneClickSpringApplication.java
├── audit/                          [conservé : hierarchy entités audit]
├── exception/                      [conservé : GlobalExceptionHandler etc.]
├── security/                       [conservé + adapté nouveau RBAC]
├── search/                         [conservé : SearchRequest, Specs]
│
├── core/
│   ├── identity/                   [User, Role, Permission, Menu, Action]
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── Permission.java
│   │   ├── Menu.java
│   │   ├── Action.java
│   │   ├── UserRepository.java
│   │   ├── UserService.java
│   │   ├── UserController.java
│   │   ├── UserDto.java
│   │   └── package-info.java       [@ApplicationModule]
│   ├── auth/                       [RefreshToken, LoginHistory, OtpRequest]
│   ├── tenant/                     [Tenant, TenantBranding, TenantFeature, CompanySettings]
│   ├── notification/               [Notification, NotificationCampaign, DeviceToken]
│   ├── media/                      [Media, FileAttachment]
│   ├── audit_log/                  [AuditLog, SystemEvent, ErrorLog, JobExecution]
│   └── configuration/              [FeatureFlag, FeatureFlagTarget, CacheConfiguration]
│
└── modules/
    ├── restaurant/                 [Restaurant, RestaurantStaff, Zone, Table, Service, BusinessHour]
    ├── reservation/                [Reservation, ReservationGuest, ReservationStatusHistory, BookingRule]
    ├── loyalty/                    [LoyaltyAccount, LoyaltyTransaction, LoyaltyRule, Redemption, Tier]
    ├── promotion/                  [Offer]
    ├── community/                  [Post, Comment, PostLike]
    ├── social/                     [Friendship, Referral]
    ├── event/                      [Event, EventParticipation]
    ├── resource_booking/           [Resource, ResourcePricing, ResourceBooking, ResourceBookingGuest]
    ├── financial/                  [Contract, Invoice, InvoiceLine, WalletTransaction]
    ├── payment/                    [Payment, PaymentMethod, Refund, PaymentTransaction]
    ├── support/                    [SupportTicket, TicketMessage, TicketAttachment]
    └── analytics/                  [RestaurantSearchDocument, ApiClient, ApiKey, Webhook, WebhookDelivery]
```

## Phasage d'implémentation

| Phase | Module | Tables | Effort |
|---|---|---|---:|
| **0** | **Setup foundation** (pom Modulith, DB enterprise, structure packages, nettoyage legacy) | — | 0.5 j |
| **1** | `core/identity` (RBAC) | 5 (User, Role, Permission, Menu, Action) | 0.5 j |
| **2** | `core/auth` | 3 (RefreshToken, LoginHistory, OtpRequest) | 0.3 j |
| **3** | `core/tenant` | 4 (Tenant, TenantBranding, TenantFeature, CompanySettings) | 0.3 j |
| **4** | `core/notification` | 3 (Notification, NotificationCampaign, DeviceToken) | 0.3 j |
| **5** | `core/media`, `core/audit_log`, `core/configuration` | 9 | 0.5 j |
| **6** | `modules/restaurant` | 6 (Restaurant, Staff, Zone, Table, Service, BusinessHour) | 0.5 j |
| **7** | `modules/reservation` | 4 (Reservation, Guest, StatusHistory, BookingRule) | 0.5 j |
| **8** | `modules/loyalty` | 5 (Account, Transaction, Rule, Redemption, Tier) | 0.5 j |
| **9** | `modules/resource_booking` | 4 (Resource, Pricing, Booking, Guest) | 0.5 j |
| **10** | `modules/financial` + `modules/payment` | 8 (Contract, Invoice, Lines, Wallet + Payment, Method, Refund, Transaction) | 0.7 j |
| **11** | `modules/community`, `modules/social`, `modules/promotion`, `modules/event`, `modules/support` | 11 | 1 j |
| **12** | `modules/analytics` + tables avancées (Search, API, Webhooks) | 6 | 0.5 j |
| **13** | **ETL data migration** legacy DB → enterprise DB | — | 1-2 j |
| **14** | Tests intégration + smoke run + tag final | — | 0.5 j |

**Total estimé** : ~8-10 jours-homme backend

## Conventions

### Naming entités
- Snake case en DB (`reservation_at`, `client_id`, `created_at`)
- camelCase en Java (`reservationAt`, `clientId`, `createdAt`)
- DTOs : `XxxDto` (lecture), `XxxCreateDto` (POST), `XxxUpdateDto` (PATCH)

### Audit standardisé (§16)
- Toutes les tables principales : `created_at`, `updated_at`, `deleted_at`, `created_by`, `updated_by`
- **Soft delete obligatoire** via `deleted_at IS NOT NULL`
- Hierarchie d'entités d'audit conservée (CreatedAtEntity/TimestampedEntity/AuditedEntity)

### Multi-tenant (§17)
- `tenant_id` dans toutes les tables métier
- RLS-equivalent géré au niveau Spring service (filtre par `tenant_id` depuis SecurityContext)

### Spring Modulith
- 1 `package-info.java` par module avec `@org.springframework.modulith.ApplicationModule(displayName = "…")`
- Communication inter-modules par events (`ApplicationEventPublisher`) — pas d'injection directe
- DTOs publics dans `api/` (visibles autres modules), internal dans `internal/` (caché)

### Sécurité
- 1 user = 1 role (§2.1) — fini les many-to-many
- `@PreAuthorize("hasRole('ADMIN')")` au niveau méthode + tests
- JWT HS256 (gardé) avec rôles depuis `User.role.code`

## ETL Data Migration (§Phase 13)

Quand toutes les tables enterprise sont en place, un script Python ou Spring Boot dédié :
1. Lire `oneclick_local` (DB legacy avec ~95 tables)
2. Transformer selon les mappings :
   - `reservations.date + heure` → `reservation_at` timestamptz
   - `loyalty_points` (1 row par mouvement) → `LoyaltyAccount.balance` + N `LoyaltyTransaction`
   - `bookable_resources` + `resource_bookings` + `loyalty_punch_cards` → `Resource` + `ResourceBooking`
   - `user_roles` many-to-many → `user.role_id` (1 rôle, on prend le plus prioritaire)
   - etc.
3. Insérer dans `oneclick_enterprise`
4. Verify (counts, sample queries)

## Frontend

Backend d'abord. Frontend Vue.js 3 + Nuxt après — branche `enterprise-vue` déjà créée, état actuel React archive.
