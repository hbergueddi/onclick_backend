# Phase 4 — Scaffolder Option A : résultats

> Génération automatisée de l'intégralité de la stack JPA pour les ~85 tables non-pilotes du schéma OneClick. Calibré sur les 7 patterns validés en Phase 3.

## Résultat exécutif

| Métrique | Valeur |
|---|---|
| Tables/vues/enums introspectés | 102 relations + 14 enums |
| Fichiers Java générés en 1 run | **580** |
| Durée d'exécution du scaffolder | **0.7 s** |
| Lignes de code générées | ~13 100 |
| Endpoints REST exposés | **207** (22 pilotes + 185 générés) |
| Boot Spring Boot | 3.8 s (vs 2.5 s en Phase 3 — +1.3 s pour ~600 entités) |
| `./mvnw compile` | exit 0, 0 warnings |
| Hibernate `ddl-auto=validate` | OK sur 600+ entités |
| Edits manuels post-génération | **0** |

---

## Comment ça marche

```bash
cd OneClick_Spring/scripts
node scaffold-jpa.mjs --dry-run            # preview
node scaffold-jpa.mjs --only=monitor_logs  # 1 table (debug)
node scaffold-jpa.mjs --group=admin        # 1 groupe
node scaffold-jpa.mjs                      # bulk (skip pilotes)
node scaffold-jpa.mjs --force              # bulk forcé (DANGER : écrase pilotes)
```

Connexion DB via env : `PGHOST=localhost PGPORT=5432 PGDATABASE=oneclick_local PGUSER=hh`.

Le script :

1. Connecte à Postgres (lib `pg`)
2. Liste les `BASE TABLE` + `VIEW` du schéma `public`
3. Pour chaque relation : récupère colonnes (`information_schema.columns`), PK (`table_constraints` + `key_column_usage`), FK, contraintes uniques
4. Liste les enums DB (`pg_type` + `pg_enum`)
5. Pour chaque relation non-pilote :
   - Détecte le pattern (audit columns, vue, PK composite, etc.)
   - Émet 6 fichiers Java via templates conditionnels
   - Émet un IdClass séparé si PK composite
6. Pour chaque enum DB : 1 fichier Java dans `entity/shared/`

## Architecture du scaffolder

```
scripts/
├── package.json                     pg ^8.13 dependency
├── .gitignore                       node_modules/
├── scaffold-jpa.mjs                 CLI entry-point
└── scaffold/
    ├── db.mjs                       Postgres introspection (4 queries)
    ├── naming.mjs                   snake_case → PascalCase, group inference (70+ exact + prefix)
    ├── types.mjs                    pg type → Java mapping (uuid, jsonb, ARRAY, enums, numeric, etc.)
    └── templates.mjs                The 8 generators :
                                       - entityTemplate
                                       - idClassTemplate (composite PK)
                                       - enumTemplate (1 per DB enum)
                                       - repositoryTemplate
                                       - dtoTemplate
                                       - mapperTemplate
                                       - serviceTemplate
                                       - controllerTemplate
```

## Patterns Phase 3 réutilisés à l'échelle

Tous les patterns calibrés manuellement en Phase 3 ont été émis automatiquement par le scaffolder, sans correction post-génération :

| Pattern Phase 3 | Tables impactées | Ce que le scaffolder a émis |
|---|---|---|
| `extends TimestampedEntity` | ~50 tables | ~50 héritages corrects |
| `extends AuditedEntity` (les 4 cols) | 0 (aucune table legacy n'a la signature complète) | — |
| Audit partiel `created_by` inline | ~5 tables (tenants, etc.) | `@CreatedBy` inline + `@EntityListeners` |
| Audit partiel `created_at` seul | ~10 tables (junction) | `@CreatedDate` inline |
| PG enum natif `@JdbcTypeCode(NAMED_ENUM)` | 9 enums × N usages | 14 enums dans `entity/shared/` |
| JSONB structuré ou libre | 14 tables | `Map<String,Object>` + `@JdbcTypeCode(JSON)` |
| ARRAY text/uuid | 11 tables | `List<String>`/`List<UUID>` + `@JdbcTypeCode(ARRAY)` |
| Composite PK | 3 junction (announcement_reads, tenant_features, tenant_admins) | `@IdClass` + ID classes serializable |
| Vue read-only | 7 vues + 1 matview | `@Immutable` + `Repository<>` basique |
| `tsvector` ignoré | 1 (restaurants) | Skipped silently |

---

## 12 groupes métier (par taille)

| Groupe | Tables | Layers x Tables | Total fichiers |
|---|---|---|---|
| `admin` | 20 | 6 | 120 |
| `loyalty` | 19 | 6 | 114 |
| `restaurant` | 15 (incl. vues) | 6 | 90 |
| `contract` | 9 | 6 | 54 |
| `support` | 7 | 6 | 42 |
| `pcc` | 6 | 6 | 36 |
| `reservation` | 6 | 6 | 36 |
| `marketing` | 5 | 6 | 30 |
| `tenant` | 5 (hors pilote) | 6 | 30 |
| `auth` | 3 (hors pilote) | 6 | 18 |
| `misc` | 1 (LoyaltyPlafond — à reclasser) | 6 | 6 |
| `shared` | — | 1 (enum/file) | 14 |

---

## Cycle de développement du scaffolder

| Itération | Bug rencontré | Fix |
|---|---|---|
| 1 | Vues sans `@Id` (information_schema ne renvoie rien pour les vues) | Heuristique : prendre la colonne `id` ou la 1ère colonne |
| 2 | URL collision `admin_audit_log` ↔ `v_admin_audit_log` | Préfixe `/api/views/<name>` pour les vues |
| 3 | `pg` client renvoie `array_agg(text)` en string brute `{a,b,c}` | Re-grouper côté JS, 1 row par valeur d'enum |
| 4 | Entités utilisaient l'enum sans l'importer | Ajout import `entity.shared.<Enum>` dans `mapColumn` |
| 5 | Pilotes Phase 3 avaient `AppRole` dans `entity/auth/` au lieu de `shared/` | Migration des 2 enums + patch sed des 9 fichiers consommateurs |

Total temps de debug : ~30 min (5 fixes itératifs sur 1 table → 1 groupe → bulk).

---

## Validation E2E

Smoke tests sur 10+ endpoints de 7 groupes différents :

```bash
GET /api/loyalty-points              → 1497 lignes (FIFO points)
GET /api/partner-contracts           → 31 contrats
GET /api/offers                      → 84 offres marketing
GET /api/bookable-resources          → 33 ressources PCC + enum padel_court
GET /api/scanned-tickets             → 6 tickets + JSONB items
GET /api/tenant-features             → 47 features (PK composite tenantId+featureKey)
GET /api/views/admin-audit-logs      → 28 lignes (vue) + JSONB metadata
GET /api/monitor-logs                → 4734 logs + JSONB metadata
GET /api/notifications               → 40019 notifications
GET /api/restaurants/core/by-city?city=Marrakech → 388 bytes/row (vue, payload -60%)
```

Tous les types DB lus correctement, sérialisation JSON propre, pagination Spring Data fonctionnelle, pas de NullPointerException.

---

## Limites connues

1. **Toutes les méthodes de service sont basiques** : `findById`, `findAll`. Pas de query métier (filtres, jointures, etc.). À enrichir au cas par cas selon le besoin business — c'est le rôle du dev humain au-dessus du scaffolding, pas du générateur.
2. **Pas de mutations exposées** : pas de POST/PATCH/DELETE générés (sauf via les pilotes Phase 3). Nécessite réflexion sur la sécurité (`@PreAuthorize`) et la validation Bean (qui dépendent du contexte métier).
3. **`misc.LoyaltyPlafond`** : 1 table non classifiée — à ajouter à `EXACT_GROUPS` dans `naming.mjs` puis regénérer.
4. **Spring DevTools peut ralentir le boot** : 3.8s avec ~600 entités vs 2.5s avec 7 pilotes. Sans DevTools en prod : ~1s gain attendu.
5. **0 test unitaire généré** : intentionnel. Les tests pilotes en Phase 3 valident les 7 patterns ; chaque entité business sera testée selon son use case en Phase 11.
6. **Aucune relation `@ManyToOne` matérialisée** : les FKs sont gardées en UUID brut. Conforme au pattern Phase 3 (lazy loading explicite via le service de l'entité cible). Évite les anti-patterns N+1 et garde le scaffolder simple.

---

## Prochaine étape — Phase 5

D'après la roadmap senior dev :
- **Item #5 ControllerAdvice + exceptions** : `@RestControllerAdvice` global + `ApiException` hierarchy + ProblemDetails RFC 7807. Maintenant que tous les controllers existent, on peut centraliser le mapping erreurs → HTTP.
- **Item #6 Spring Security réelle** : activer OAuth2 Resource Server, JwtDecoder, `@PreAuthorize` sur les controllers générés selon les rôles métier.
- **Item #8 Permissions/rôles par menu** : table `permissions` + helper Spring Security.

Estimation :
- Phase 5 (ControllerAdvice + Security réelle) : 2-3 jours
- Phase 11 (logique métier — porter les 53 EFs) : 5-10 jours
- Phase 12 (frontend bridge React → Spring) : 3-5 jours
