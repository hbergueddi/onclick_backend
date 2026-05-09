# Conventions Lombok — OneClick Spring Backend

> **Version Lombok** : `1.18.46` (managed par Spring Boot BOM 4.0.6, compatible Java 26)
> **POC pilote** : `EntityStatus.java` (commit Phase 4 mai 2026)
> **Statut** : installation OK + IntelliJ annotation processing actif. Adoption progressive sur les ~95 entités JPA, à faire au cas par cas.

---

## TL;DR — Convention senior projet

| Annotation Lombok | Sur entités JPA | Sur DTOs | Sur services |
|---|---|---|---|
| `@Getter` (classe) | ✅ Oui | N/A (records) | N/A |
| `@Setter` (classe) | ❌ Non — granulaire au field | N/A | N/A |
| `@Setter` (field) | ✅ Sur les fields mutables business | N/A | N/A |
| `@NoArgsConstructor(access = PROTECTED)` | ✅ Oui (remplace le `protected XxxEntity() {}` JPA) | N/A | N/A |
| `@AllArgsConstructor` | ❌ **Jamais** | N/A | ❌ |
| `@RequiredArgsConstructor` | ❌ Non sur entités | N/A | ✅ Constructor injection |
| `@Builder` | ❌ **Jamais sur entités JPA** | ✅ Sur DTOs complexes | ❌ |
| `@Data` | ❌ **JAMAIS** (combine `@EqualsAndHashCode` toxique) | ❌ | ❌ |
| `@EqualsAndHashCode` | ❌ **JAMAIS** sur JPA — casse anti-proxy | ✅ Sur DTOs | N/A |
| `@ToString` | ⚠️ `onlyExplicitlyIncluded=true` uniquement (sinon cycle infini sur les `@ManyToOne`) | ✅ | ✅ |
| `@Slf4j` | N/A | N/A | ✅ Recommandé sur services/controllers |

---

## Pourquoi ces règles

### ❌ `@Data` / `@EqualsAndHashCode` interdits sur entités JPA

Lombok génère `equals`/`hashCode` basés sur **tous les champs**. Trois problèmes critiques :

1. **Anti-pattern proxy LAZY** : Comparer un `HibernateProxy` à une entité hydratée renvoie `false` même si même `id`, parce que les classes diffèrent (`Foo$HibernateProxy` vs `Foo`). Notre pattern senior (cf. *Vlad Mihalcea*) utilise `HibernateProxy.getPersistentClass()` pour résoudre la classe effective.
2. **Cycle infini sur `@ManyToOne`** : `@EqualsAndHashCode` lit `@ManyToOne reservation` → trigger LAZY load → lit `@OneToMany guests` du parent → boucle.
3. **Identité métier vs identité technique** : Pour JPA, l'identité d'une entité est son `id` (PK), pas la valeur de tous ses champs. Deux `Reservation` avec mêmes `client/date/heure` mais ID différents sont **différentes** (cas du seed dupliqué, par exemple).

→ On garde **`equals` / `hashCode` manuels anti-proxy** sur toutes les entités JPA :

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    Class<?> oEffective = o instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getPersistentClass()
        : o.getClass();
    Class<?> thisEffective = this instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getPersistentClass()
        : this.getClass();
    if (thisEffective != oEffective) return false;
    return id != null && Objects.equals(id, ((MyEntity) o).getId());
}

@Override
public int hashCode() {
    return this instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
        : getClass().hashCode();
}
```

### ❌ `@Builder` interdit sur entités JPA

Hibernate construit l'entité via **réflexion + constructor sans-args + setters**. Un `@Builder` :
- Casse les invariants (peut produire une entité avec `id != null` jamais persistée → état "fake managed").
- Inutile : la création passe par les services, pas par builder applicatif.
- Sur les **DTOs**, `@Builder` est OK et utile (records Java 26 le rendent moins nécessaire).

### ❌ `@Setter` au niveau classe interdit

Génère un setter pour **tous** les fields, y compris :
- `@Id id` — la PK doit être immuable une fois assignée.
- Audit fields `@CreatedDate created_at`, `@LastModifiedDate updated_at` — gérés par Spring Data Auditing, le code applicatif ne doit jamais les modifier.
- UUID raccourcis `insertable=false, updatable=false` — Hibernate plante si on essaie de les setter directement.

→ **Setter granulaire au field uniquement** sur les fields mutables business.

### ⚠️ `@ToString` avec précaution

Sans précaution :
```java
@ToString public class Reservation { @ManyToOne Profile client; @ManyToOne Restaurant restaurant; }
```
→ `reservation.toString()` charge LAZY le client + le resto. Si on log dans une boucle de 100 résas → 200 SELECT cachés.

→ Pattern safe :
```java
@ToString(onlyExplicitlyIncluded = true)
public class Reservation {
    @Id @ToString.Include UUID id;
    @ToString.Include LocalDate date;
    @ManyToOne Profile client;  // pas inclus
}
```

---

## Pattern d'usage standard sur une entité JPA

```java
@Entity
@Table(name = "my_entities")
@Getter                                                   // ✅ tous les getters
@NoArgsConstructor(access = AccessLevel.PROTECTED)        // ✅ remplace `protected MyEntity() {}`
public class MyEntity extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    // ↑ pas de @Setter — l'@Id est immuable

    @NotBlank
    @Column(name = "name", nullable = false)
    @Setter  // ✅ field mutable business
    private String name;

    @Column(name = "created_by")
    private UUID createdBy;
    // ↑ pas de @Setter — audit, géré par @CreatedBy

    // ─── @ManyToOne LAZY + UUID raccourci ─────────────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;
    // ↑ pas de @Setter — raccourci read-only (Hibernate écrit via la jointure)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    @Setter  // ✅ écrit via cette propriété
    private Tenant tenant;

    // ─── Constructor business public si besoin (pas géré par Lombok) ─
    public MyEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    // ─── equals/hashCode anti-proxy MANUELS ───────────────────────
    @Override
    public boolean equals(Object o) { /* … pattern anti-proxy … */ }

    @Override
    public int hashCode() { /* … pattern anti-proxy … */ }
}
```

---

## Pattern d'usage standard sur un service Spring

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor    // ✅ génère le constructor avec final fields → DI
@Slf4j                      // ✅ génère `private static final Logger log = …`
public class MyService {

    private final MyRepository repository;
    private final EntityStatusService statusService;

    @Transactional
    public MyDto create(MyCreateDto dto) {
        log.debug("Creating MyEntity with name={}", dto.name());
        // …
    }
}
```

---

## Pattern d'usage standard sur un DTO

Records Java 26 sont préférés à Lombok pour les DTOs simples :

```java
public record MyDto(UUID id, String name, String status, Instant createdAt) {}
```

Pour les DTOs complexes (création, payloads imbriqués), Lombok :

```java
@Builder            // ✅ OK sur DTOs (pas entités)
@With               // ✅ pour les record-like avec mutation contrôlée
public record MyComplexDto(...) {}
```

---

## Migration progressive — checklist

Pour migrer une entité existante vers Lombok :

- [ ] Ajouter `@Getter` au niveau classe → supprimer manuellement les getters.
- [ ] Ajouter `@NoArgsConstructor(access = AccessLevel.PROTECTED)` → supprimer le `protected XxxEntity() { /* JPA */ }`.
- [ ] Identifier les fields **mutables business** → ajouter `@Setter` au field (pas au niveau classe).
- [ ] **NE PAS TOUCHER** aux `equals` / `hashCode` / `toString` existants.
- [ ] **NE PAS TOUCHER** aux helpers bidirectionnels (`addX`, `removeX`).
- [ ] **NE PAS TOUCHER** aux setters package-private (`setReservation` côté child d'aggregate).
- [ ] Compile : `./mvnw clean compile`.
- [ ] Test E2E : `./scripts/test-e2e.sh`.

---

## Vérifier qu'IntelliJ exploite bien Lombok

1. **Plugin Lombok** : Settings → Plugins → vérifier que "Lombok" est installé et activé. Sinon : marketplace → install → restart.
2. **Annotation processing** : Settings → Build, Execution, Deployment → Compiler → Annotation Processors → "Enable annotation processing" coché. Sinon, IntelliJ affichera les getters Lombok comme "method not found" même si Maven compile OK.
3. **Vérification rapide** : ouvrir `EntityStatus.java`, faire `Cmd+B` (Go to Declaration) sur `entityStatus.getCode()` depuis un autre fichier — si IntelliJ navigue vers le field `code`, Lombok est correctement intégré.

---

## Cas que Lombok ne couvre pas (à laisser manuels)

- **Helpers bidirectionnels** (`addGuest` / `removeGuest`) : nécessitent une logique de cohérence à 2 côtés.
- **Setters package-private côté child** d'aggregate (`void setReservation(...)` appelé par les helpers du parent) : Lombok ne supporte pas la visibilité `package-private` → on les écrit à la main.
- **Setters avec validation** (ex: `setStatus` qui rejette une transition d'état invalide) : Lombok ne fait que de l'assignation. Si on veut valider, on l'écrit à la main.
- **`@MapsId`** sur les composite IDs (TenantAdmin, AnnouncementRead, TenantFeature) : pattern JPA spécifique, indépendant de Lombok.
- **`@OneToMany` collections + helpers `addX`/`removeX`** sur aggregate roots : Lombok peut générer le getter mais pas les helpers — on les garde manuels.
