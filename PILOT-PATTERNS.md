# Phase 3 — Patterns pilotes pour le scaffolder

> Catalogue des 7 entités pilotes validées E2E avant de générer les 86 autres en Phase 4 (Option A — scaffolder custom).
>
> Ce document sert de **spec d'entrée** pour le générateur : chaque pattern correspond à un cas que le scaffolder doit savoir reconnaître et émettre. La couverture des 7 ≈ 90 % du schéma.

---

## Vue d'ensemble

| # | Table | Pattern principal | Layer count | Validé |
|---|---|---|---|---|
| 1 | `user_roles` | Enum DB + FK auth.users + sans timestamps | 6 | ✅ |
| 2 | `profiles` | UUID PK + ARRAY + `extends TimestampedEntity` | 7 | ✅ |
| 3 | `restaurants` | JSONB structuré + 2× ARRAY + tsvector ignoré | 7 | ✅ |
| 4 | `reservations` | Enum Unicode (accents FR) + LocalDate + 2 FKs | 7 | ✅ |
| 5 | `tenants` | JSONB libre + audit partiel (`created_by` seul) | 6 | ✅ |
| 6 | `tenant_admins` | PK composite via `@IdClass` | 7 | ✅ |
| 7 | `v_restaurants_core` | Vue read-only via `@Immutable` + Repository basique | 6 | ✅ |

Chaque pilote = entity + repository + DTO record + mapper MapStruct + service + controller + (parfois) DTO de write distinct.

---

## Pattern 1 — Enum DB + FK auth.users sans audit

**Référence : `entity/auth/UserRole.java`**

Décisions :
- `@Id UUID id` avec default DB `gen_random_uuid()` → l'app ne fournit pas l'ID, c'est `INSERT ... DEFAULT` qui le génère.
- `@Enumerated(EnumType.STRING) + @JdbcTypeCode(SqlTypes.NAMED_ENUM)` → binding natif PG enum (sans cette annotation Hibernate envoie un VARCHAR et PG refuse).
- `@Column(columnDefinition = "app_role")` indique à Hibernate le type enum natif.
- FK `user_id` vers `auth.users(id)` gardée comme **UUID brut** — pas de `@ManyToOne` (auth est un schéma système hors mapping).
- `@UniqueConstraint` déclarée pour matcher la contrainte DB existante.
- Pas d'héritage `TimestampedEntity` (pas de `created_at`/`updated_at`).

```java
@Entity
@Table(name = "user_roles", uniqueConstraints = @UniqueConstraint(...))
public class UserRole {
  @Id @Column(name = "id") private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "role", columnDefinition = "app_role") private AppRole role;
}
```

Le scaffolder doit émettre ce template pour les **9 tables avec colonne enum DB** : `bookable_resources`, `loyalty_punch_cards`, `point_distributions`, `reservations`, `resource_bookings`, `restaurant_staff`, `tenant_announcements`, `user_roles`, `tenant_admins` (sur `role`).

---

## Pattern 2 — UUID PK + ARRAY + héritage TimestampedEntity

**Référence : `entity/auth/Profile.java`**

Décisions :
- `extends TimestampedEntity` → hérite `created_at` + `updated_at` automatiquement.
- ARRAY text[] → `List<String>` via `@JdbcTypeCode(SqlTypes.ARRAY)`. Hibernate 6+ natif, **pas besoin** de `hibernate-types-52`.
- `numeric(3,1)` → `BigDecimal` avec `precision`/`scale` déclarés (jamais `double`).
- FKs vers tables non-pilotes (`tenant_id`, `tenant_group_id`) → UUID brut. Pattern explicite : on ne matérialise des `@ManyToOne` que si le besoin métier est clair (lazy/eager loading), sinon UUID + lookup explicite via le service de l'entité cible.
- PATCH partiel : `ProfileUpdateDto` (record) + MapStruct `@BeanMapping(NullValuePropertyMappingStrategy.IGNORE)` → seuls les champs non-null sont copiés (sémantique HTTP PATCH).

Le scaffolder doit gérer **~70 tables avec `created_at` + `updated_at`** (même pattern d'héritage).

---

## Pattern 3 — JSONB structuré + tsvector ignoré

**Référence : `entity/restaurant/Restaurant.java` (34 colonnes)**

Décisions :
- JSONB structuré (ex: `opening_hours`) → `List<OpeningHourSlot>` où `OpeningHourSlot` est un **record** Java 26 (`@JdbcTypeCode(SqlTypes.JSON)`). Jackson désérialise via le constructor canonique des records.
- JSONB libre (ex: `features` chez `tenants`) → `Map<String, Object>` (cf Pattern 5).
- Multiples ARRAY (`tags`, `google_photos`) → mêmes annotations qu'au Pattern 2.
- `tsvector search_vector` → **NON mappé**. Hibernate `validate` ne se plaint que si l'entité déclare une colonne absente en DB, pas l'inverse. Donc on omet simplement le champ. La recherche fulltext passera par `@Query(nativeQuery=true)` ou RPC en Phase 11.
- 34 colonnes → la verbosité est inhérente. Le scaffolder ne fait pas de "magie" pour réduire ; les getters/setters sont explicites pour faciliter la review.

Tables JSONB structurées (à modéliser avec record dédié) : `bookable_resources.opening_hours`, `bookable_resources.pricing`, `resource_bookings.pricing_snapshot`, `partner_contracts.contract_snapshot`, `scanned_tickets.items`, `rule_templates.config`. → 6 records à générer.

Tables JSONB libres (Map) : `admin_audit_log.metadata`, `admin_audit_log.diff`, `email_bounces.raw_event`, `monitor_logs.metadata`, `tenants.features`, `custom_roles.permissions`, etc. → ~8 cas.

---

## Pattern 4 — Enum Unicode (accents) + LocalDate

**Référence : `entity/reservation/Reservation.java` + `ReservationStatus`**

Décision **anti-conformiste** validée :
- L'enum DB `reservation_status` a 10 valeurs avec accents français (`demandée`, `confirmée`, `honorée`, `refusée`).
- L'enum Java reproduit ces noms **EXACTEMENT** (Unicode autorisé dans les identifiers Java) pour avoir un mapping `@Enumerated(EnumType.STRING)` natif sans AttributeConverter.
- `@SuppressWarnings("NonAsciiCharacters")` sur l'enum pour silencer le warning de style.

```java
@SuppressWarnings("NonAsciiCharacters")
public enum ReservationStatus {
  demandée, confirmée, placée, terminée, annulée, honorée,
  no_show, refusée, contre_proposition, en_attente
}
```

URL params : Spring URL-decode automatiquement. `?status=demand%C3%A9e` → `ReservationStatus.demandée`.

Aussi validé :
- `date date` → `LocalDate` (pas `Instant`)
- `heure text` → `String` "HH:MM" (préservation du format frontend, pas `LocalTime`)
- `@DateTimeFormat(iso = ISO.DATE)` sur le param controller pour parser `YYYY-MM-DD`

---

## Pattern 5 — Audit partiel (created_by seul, sans modified_by)

**Référence : `entity/tenant/Tenant.java`**

Cas particulier découvert dans l'audit du schéma : la table `tenants` a `created_by` mais **PAS** `modified_by`. Idem pour `tenant_admins` qui a `invited_by` mais pas `modified_by`.

Solution :
- Pas d'extension `AuditedEntity` (qui exigerait les 2 colonnes — Hibernate `validate` planterait).
- `extends TimestampedEntity` (created_at + updated_at hérités).
- Déclaration manuelle inline :
  ```java
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;
  ```

Le scaffolder doit détecter ce cas (présence de `created_by` SANS `modified_by`) et émettre la déclaration inline plutôt que l'héritage `AuditedEntity`.

---

## Pattern 6 — PK composite via @IdClass

**Référence : `entity/tenant/TenantAdmin.java` + `TenantAdminId.java`**

3 tables junction dans le schéma : `tenant_admins`, `tenant_features`, `announcement_reads`.

Décisions :
- `@IdClass(TenantAdminId.class)` choisi sur `@EmbeddedId` pour 2 raisons :
  1. Accès direct aux champs (`tenantAdmin.getTenantId()`) sans wrapper `id`.
  2. Spring Data dérive les queries naturellement (`findByTenantIdAndUserId`).
- `TenantAdminId` est un POJO classique (PAS un record — JPA exige no-args + setters), implémente `Serializable`, `equals` + `hashCode` basés sur les 2 champs (mandatory pour le persistence context).
- Audit partiel : pas de `updated_at` → on ne peut pas hériter de `TimestampedEntity`. On déclare `@CreatedDate` + `@EntityListeners(AuditingEntityListener.class)` au niveau entité directement.

---

## Pattern 7 — Vue read-only via @Immutable

**Référence : `entity/restaurant/RestaurantCoreView.java`**

7 vues dans le schéma + 1 matview = 8 entités à générer ainsi.

Double sécurité :
1. **Hibernate `@Immutable`** : aucun UPDATE jamais émis, même si save() est appelé.
2. **`@Column(insertable=false, updatable=false)` sur tous les champs** : ceinture + bretelles.
3. **Repository `extends Repository<,>` (interface basique)** au lieu de `JpaRepository` → retire `save`, `delete` du contrat compile-time. On ne peut pas accidentellement appeler un mutator.

Bénéfice perf : payload **-60 %** (388 vs 963 bytes) entre `/restaurants/{id}` (33 cols) et `/restaurants/core` (12 cols). À reproduire pour les listes via les autres vues.

---

## Patterns NON couverts par les 7 pilotes

À traiter ad-hoc pendant Phase 4, idéalement quand le scaffolder les rencontre et que le compile casse :

| Pattern | Tables connues | Solution probable |
|---|---|---|
| `TIME` (sans timezone) | `loyalty_punch_cards`? | `LocalTime` |
| `INTERVAL` | aucune confirmée | `Duration` |
| `tstzrange` (range type) | aucune confirmée | Type custom `Range<Instant>` Hibernate |
| Materialized view | `mv_reservations_summary` | Comme vue + `@Table(name="mv_...")` |
| Generated column (always) | aucune confirmée | `@Generated(GenerationTime.ALWAYS)` |
| Tables totalement vides en prod (27) | divers | Mappées normalement, juste pas de E2E test possible |

---

## Ce que le scaffolder Option A doit produire

Pour chacune des 86 tables restantes, **6 fichiers** :

1. `entity/{group}/{Entity}.java` — choix de superclass selon l'audit columns détectées
2. `repository/{group}/{Entity}Repository.java` — `JpaRepository<E,UUID>` (ou `Repository<E,ID>` pour vues)
3. `dto/{group}/{Entity}Dto.java` — record Java 26
4. `mapper/{group}/{Entity}Mapper.java` — interface MapStruct
5. `service/{group}/{Entity}Service.java` — `@Service @Transactional(readOnly=true)` + méthodes find* basiques
6. `controller/{group}/{Entity}Controller.java` — GET endpoints minimaux (`{id}`, listes, count)

Plus, par groupe métier dans `entity/{group}/`, les **enums** et **records JSONB** détectés.

Groupes proposés (ordre de génération recommandé, dépendances FK respectées) :
1. **auth** : `user_roles` ✅, `profiles` ✅
2. **tenant** : `tenants` ✅, `tenant_admins` ✅, `tenant_features`, `tenant_branding`, `tenant_events`, `tenant_announcements`, `announcement_reads`
3. **restaurant** : `restaurants` ✅, `restaurant_groups`, `restaurant_staff`, `restaurant_services`, `restaurant_zones`, `restaurant_tables`, `client_visible_ratings` (+vues)
4. **reservation** : `reservations` ✅, `reservation_guests`, `friend_groups`, `friend_group_members`, `friendships`
5. **loyalty** : `loyalty_points`, `loyalty_punch_cards`, `point_distributions`, `point_gifts`, `gain_rules`, `scanned_tickets`
6. **marketing** : `offers`, `promo_notification_requests`, `referrals`, `tenant_announcements`
7. **pcc** : `bookable_resources`, `resource_bookings`, `seminar_requests`, `tenant_events`
8. **admin** : `admin_audit_log`, `admin_notifications`, `monitor_logs`, `email_bounces`
9. **contract** : `partner_contracts`, `contract_templates`, `oneclick_hi_invoices`, `company_settings`
10. **support** : `support_tickets`, `client_ratings`, `no_show_disputes`, `rule_templates`, `custom_roles`

---

## Métriques Phase 3

| Indicateur | Valeur |
|---|---|
| Tables pilotes validées | 7 / 7 |
| Lignes Java écrites | ~2300 |
| Fichiers créés | 47 (entities, repos, DTOs, mappers, services, controllers, value records, ID class, audit superclasses) |
| Endpoints REST exposés | 22 GET + 1 POST + 1 DELETE + 1 PATCH |
| Couverture schéma estimée | ~90 % des cas (les 10 % restants traités ad-hoc en Phase 4) |
| Boot Spring time | 2.0 - 2.7 s |
| Latence GET /api/* (no cache) | 2-5 ms |

Toute la stack tourne sur Spring Boot 4.0.6 + Java 26 + Hibernate 7 + MapStruct 1.6.3 + Springdoc 2.8.6.
