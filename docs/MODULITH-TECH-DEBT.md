# Spring Modulith — dette technique post-consolidation

> Tracking des couplages cross-module qui empêchent le passage en `Type.CLOSED` strict pour certains modules. Tous les modules sont aujourd'hui en `Type.OPEN` (Modulith verify vert).

## Statut actuel (post monolith-v1.1-modulith-split)

| Module | Structure api/internal | Type | Dette |
|--------|------------------------|------|-------|
| modules/loyalty | ✅ split | **CLOSED** | — pilote propre |
| modules/payment | ✅ split | OPEN | mapping `Dto.from(Entity)` inversé en `Entity.toDto()` requis |
| modules/resource_booking | ✅ split | OPEN | idem |
| modules/support | ✅ split | OPEN | idem |
| modules/community | ✅ split | OPEN | idem |
| modules/social | ✅ split | OPEN | idem |
| modules/analytics | ✅ split | OPEN | idem |
| modules/promotion | ✅ split | OPEN | `Offer.@ManyToOne Restaurant` → UUID + lookup |
| modules/financial | ✅ split | OPEN | `Contract/Invoice/WalletTransaction.@ManyToOne Restaurant` → UUID |
| modules/reservation | ✅ split | OPEN | `Reservation/BookingRule.@ManyToOne Restaurant/MealService/RestaurantTable` → UUIDs |
| modules/event | ✅ split | OPEN | `Event.@ManyToOne Restaurant` → UUID |
| modules/restaurant | ✅ split | OPEN | importé par 4 modules ci-dessus |
| core/identity | ✅ split | OPEN | `User.@ManyToOne Tenant` → UUID |
| core/auth | ✅ split | OPEN | imports `core.identity.internal.User, UserRepository` |
| core/tenant | ✅ split | OPEN | racine, mais Tenant Entity exposée |
| core/notification | ✅ split | OPEN | — |
| core/media | ✅ split | OPEN | `FileAttachment` → User reference |
| core/audit_log | ✅ split | OPEN | `AuditLog` → User + Tenant references |
| core/configuration | ✅ split | OPEN | — |

## Dette catégorisée

### 1. Couplages d'Entity cross-module (4 hubs)

| Entity hub | Modules consommateurs | Refacto requise |
|-----------|----------------------|------------------|
| `core.tenant.internal.Tenant` | identity, audit_log, restaurant | `@ManyToOne Tenant` → `UUID tenantId` + lookup service si besoin |
| `core.identity.internal.User` | auth, audit_log, media | idem |
| `modules.restaurant.internal.Restaurant` | promotion, financial, reservation, event | idem |
| `core.identity.internal.UserRepository` | auth (AuthService.findUserBy...) | introduire `IdentityApi.findUserBy*` dans `core.identity.api` |

### 2. Mapping DTO ↔ Entity (12 modules)

Pattern à inverser dans les `*Dtos.java` de `api/` :

```java
// AVANT (api/ dépend de internal/ — violation Type.CLOSED)
public static PaymentDto from(Payment p) { return new PaymentDto(...); }

// APRÈS (internal/ dépend de api/ — autorisé)
public PaymentDto toDto() { return new PaymentDto(...); }   // dans Entity Payment
```

Pilote `modules/loyalty` est l'exemple à suivre.

### 3. Imports static wildcard

Le pattern `import static com.onesley.oneclick.modules.<x>.api.<X>Dtos.*;` pollue le namespace du Controller. Acceptable, mais MapStruct serait plus propre à terme.

## Plan de remédiation (sprint dédié estimé 2-3 jours)

### Étape 1 — Découpler Restaurant (4 modules clients)

```java
// AVANT
@ManyToOne(fetch = LAZY)
@JoinColumn(name = "restaurant_id")
private Restaurant restaurant;

// APRÈS
@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;
```

+ adapter `Offer.getRestaurant().getName()` → `restaurantCatalogService.findById(offer.getRestaurantId()).getName()` dans Service (pas dans Entity).

### Étape 2 — Découpler Tenant (3 modules clients)
Idem avec `tenantId` UUID + lookup via `TenantService.findById`.

### Étape 3 — Découpler User (3 modules clients)
Idem avec `userId` UUID + lookup via `UserService.findById`.

### Étape 4 — Inverser tous les `*Dto.from(Entity)` en `Entity.toDto()`

Mécanique. Peut être automatisé en script ou via MapStruct + un Mapper par module dans `internal/`.

### Étape 5 — Passer tous les modules en `Type.CLOSED`

```java
@ApplicationModule(type = Type.CLOSED, allowedDependencies = {"shared", "core::audit_log", "core::tenant"})
```

### Étape 6 — Re-vérifier `mvn test -Dtest=ModularityTests`

Doit rester vert avec un graphe d'isolation strict.

## Pourquoi remettre à plus tard ?

- ✅ La consolidation monolithique est livrée (Phase A — split api/internal partout)
- ✅ ModularityTests passe vert (pas de cycles entre modules, juste des Entity cross-module)
- ✅ La discipline est **visible** dans le code (un dev voit où vont les Entity vs DTOs)
- ⚠️ Le découplage strict casse temporairement des invariants Hibernate (lazy fetch, queries JPA) → mérite un sprint dédié avec sa propre validation E2E
- ⚠️ MapStruct (option) demande de revisiter tous les Mapper — sprint séparé aussi

**Date de cette dette** : 12 mai 2026, post-tag `monolith-v1.1-modulith-split`.
