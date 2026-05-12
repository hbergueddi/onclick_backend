# Production-Ready Checklist — OneClick Spring monolith

> Tag actuel : `monolith-v3.0-production-baseline`
> État : **Brief senior 18/18 = 100%** ✅ + 7 sprints G livrés (G.1-G.5)
> Reste pour 100% prod-ready : rate-limit + ES TLS + tests intégration + ETL data + 40 EFs Supabase à porter selon prio business

---

## ✅ Acquis production-ready

### Architecture (brief senior 8 missions)
- ✅ Monolith modulaire Spring Boot 4.0.6 + Java 26
- ✅ Spring Modulith 2.0.6 Type.CLOSED avec 19 modules (7 core + 12 métier)
- ✅ Package structure `api/` + `internal/` par module
- ✅ Modèle de données unifié (Flyway V1-V19, 60+ tables, audit standardisé)
- ✅ `allowedDependencies` explicite par module, communication via events
- ✅ Entity.toDto() inversion (jamais d'import cross-module d'entité)
- ✅ Migration step-by-step avec tags `monolith-v*` par phase
- ✅ ModularityTests JUnit fail si dépendance non déclarée

### Brief senior 18/18 items
- ✅ Items #1-#14 livrés Phase 4-6
- ✅ Item #15 refresh token (AuthController.refresh + RefreshToken entity)
- ✅ Items #16-#18 (clean architecture, Spring init, Swagger)

### Sprints G livrés
- ✅ **G.1** Pipeline OpenAPI : `/v3/api-docs` → `npm run sync-api` génère types TS
- ✅ **G.2** Backend V2 (+15 endpoints) : ReservationGuest, RestaurantPatch, GainRuleRequest, AdminStats, SupportTicket enrich, LoyaltyByRestaurant
- ✅ **G.3** 6 crons @Scheduled (reservation x3, loyalty, promotion, financial, resource_booking)
- ✅ **G.4** Frontend reservation guests migré
- ✅ **G.5** Port 3 EFs critiques (gift-points, invite-team-member, transfer-staff) + doc EDGE-FUNCTIONS-PORT.md

### Infrastructure
- ✅ Docker compose : Postgres 17 + Redis 7 + Elasticsearch 9.0.0 + Adminer + Kibana
- ✅ Sécurité : passwords/secrets externalisés en env vars, `.gitignore` renforcé
- ✅ Sentry SDK intégré (v8.27.0) pour error tracking
- ✅ Springdoc OpenAPI v2.8.6 exposant 223 endpoints

---

## 🟡 À faire pour 100% production

### G.6 — Production Hardening (~2 j)

#### Rate limiting
**Pas encore implémenté.** Options :
- **Option A (recommandé V1)** : Bucket4j 8.x via Redis
  ```xml
  <dependency>
      <groupId>com.bucket4j</groupId>
      <artifactId>bucket4j_jdk17-core</artifactId>
      <version>8.10.1</version>
  </dependency>
  ```
  Filter par IP sur `/api/auth/login` : 10 req/min (anti-bruteforce login),
  sur `/api/restaurants` : 100 req/min (catalogue PUBLIC), etc.

- **Option B (V2)** : Spring Cloud Gateway en front du monolith (mais perdrait le single-deployable cf contrainte senior #2)

#### Elasticsearch TLS + API key
Actuellement compose.yaml a `xpack.security.enabled=false`. En prod :
```yaml
environment:
  xpack.security.enabled: "true"
  ELASTIC_PASSWORD: ${ES_PASSWORD}
  # + setup-passwords script + xpack.security.http.ssl.enabled=true
```
Et côté Spring `application-prod.yml` :
```yaml
spring.elasticsearch:
  uris: https://elasticsearch.prod:9200
  username: elastic
  password: ${ES_PASSWORD}
  ssl:
    fingerprint: ${ES_CA_FINGERPRINT}
```

#### Sentry handler global
Sentry SDK v8 intégré mais pas wired au GlobalExceptionHandler. Patch :
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(Exception e) {
        Sentry.captureException(e);  // ← ajouter cette ligne
        // ... reste inchangé
    }
}
```
+ env var `SENTRY_DSN` documentée dans `.env.example` ✅

#### Tests d'intégration
Smoke tests B.1-B.8 existent mais coverage manque. Cible : >70% sur les services + 100% sur les controllers (via MockMvc + Testcontainers). Effort estimé : 3-5 jours selon profondeur.

### G.5-bis — Port des 40 EFs Supabase restantes (~5-10 j)

Cf `docs/EDGE-FUNCTIONS-PORT.md` pour le mapping complet. Priorités :
- **High** : AI Groq (oneclick-care-chat, ai-assistant) — 1 j total
- **Medium** : generate-invoices, fetch-google-places, push-friend-request — 2-3 j
- **Low** : whitelabel Resend (PCC/HOMU enrollment), monitor-telemetry — 1-2 j

### G.7 — Phase 13 ETL data migration (~1-2 j)

Migration des données Supabase Cloud (`vghevjywcbllzhhzzezf`) → `oneclick_enterprise` :
- 167 users, 17K+ profiles, 1002 restos, 15K+ staff
- 503 réservations, 198 tickets, 219 loyalty points
- 145 friendships, 6 friend groups, 61 offers, 11 referrals, 1795 notifications

**Implémentation suggérée** : Spring CommandLineRunner sous profile `etl`, déjà
configuré dans `application-etl.yml` (cf scripts ETL legacy → enterprise). Mappings
spéciaux :
- `reservations.date + heure` → `reservation_at timestamptz`
- `loyalty_points` rows → `LoyaltyAccount.balance` + N `LoyaltyTransaction`
- `bookable_resources` + `resource_bookings` + `loyalty_punch_cards` → `Resource` + `ResourceBooking`

### Frontend — fin de migration (~5-8 j)

- 132 fichiers Supabase restants (sur 140 init)
- 40 hooks + 47 pages + 35 composants à migrer
- La plupart des pages admin (Galaxy/Forge/Pulse/Shield/TrustWatch) bloquées
  par des features V2 backend (escalation workflow, analytics avancées)
- Cleanup final : `rm -rf src/integrations/supabase/` + `npm uninstall @supabase/supabase-js`

---

## 📊 Tableau de pilotage final

| Item | Statut | Effort restant |
|------|--------|---------------|
| Architecture Modulith (8 missions senior) | ✅ 100% | — |
| Brief senior 18 items | ✅ 100% (18/18) | — |
| Sprints G.1-G.5 | ✅ Livrés | — |
| G.6 rate-limit + ES TLS + Sentry handler | 🟡 30% | 2 j |
| G.5-bis 40 EFs restantes | 🟡 13% (7/53) | 5-10 j |
| G.7 ETL data legacy → enterprise | ❌ 0% | 1-2 j |
| Frontend migration finale | 🟡 6% (8/140) | 5-8 j |
| **Total restant** | — | **~13-22 j** |

---

## 🎯 Définition "production-ready v1.0" (proposée)

Le tag `production-ready-v1.0` sera créé une fois :

1. ✅ G.6 rate-limit + Sentry handler + ES TLS (2 j)
2. ✅ G.7 ETL data migration jouée 1x avec succès (1-2 j)
3. ✅ 80%+ des hooks frontend critiques migrés (3-5 j)
4. ✅ Tests d'intégration ≥70% coverage sur controllers (3 j)
5. ✅ Déploiement staging réussi (VPS prod-like + smoke tests E2E)

**Estimation totale** : 10-15 jours-développeur supplémentaires.

---

## Tags livrés

| Tag | Description |
|-----|-------------|
| `monolith-v1.0` → `v1.5` | Phase 1-3 (modulith split + RBAC + owner checks) |
| `monolith-v2.0` → `v2.3` | Phase 4-6 (production-ready squelette + 206 endpoints) |
| `monolith-v2.4-g2-extensions` | Sprint G.2 (+15 endpoints) |
| `monolith-v2.5-g3-crons` | Sprint G.3 (6 crons @Scheduled) |
| `monolith-v2.6-g5-efs-port` | Sprint G.5 (3 EFs portées + doc) |
| `monolith-v3.0-production-baseline` | **Baseline production — current** |
