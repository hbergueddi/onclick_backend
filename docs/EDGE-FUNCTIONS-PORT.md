# Port des Edge Functions Supabase → Spring monolith

> Mapping de toutes les Edge Functions Supabase (legacy) vers leurs équivalents
> Spring (livrés en sprints B + G). Source de vérité pour le sprint G.5+ futur.

**État au tag `monolith-v2.6-g5-efs-port`** : **7/53 portées** (13%).

## ✅ Edge Functions portées (7)

| EF Supabase | Spring équivalent | Sprint |
|-------------|-------------------|--------|
| `snap2earn` | `POST /api/loyalty/snap2earn` | B.8 |
| `ocr-receipt` | `POST /api/loyalty/ocr-receipt` (OCR.space stub mode) | B.8 |
| `send-promo-push` | `POST /api/notifications/push/promo` (FCM stub mode) | B.8 |
| `send-reservation-push` | `POST /api/notifications/push/reservation` (FCM stub mode) | B.8 |
| `gift-points` | `POST /api/loyalty/gift` (atomic débit+crédit) | G.5 |
| `invite-team-member` | `POST /api/restaurants/staff/invite` | G.5 |
| `transfer-staff` | `POST /api/restaurants/staff/transfer` | G.5 |

## ✅ Cron jobs portés (6 via @Scheduled)

| EF Supabase (pg_cron) | Spring @Scheduled | Sprint |
|-----------------------|-------------------|--------|
| `expire-unanswered-reservations` | `ReservationCronJobs.expireUnansweredReservations()` (15 min) | G.3 |
| `send-reservation-reminders-J1` | `ReservationCronJobs.sendReservationRemindersJ1()` (J-1 9h Maroc) | G.3 |
| `send-reservation-reminders-H2` | `ReservationCronJobs.sendReservationRemindersH2()` (15 min) | G.3 |
| `process-expired-points` | `LoyaltyCronJobs.processExpiredPoints()` (3h UTC) | G.3 |
| `expire-promotions` | `PromotionCronJobs.expirePromotions()` (4h UTC) | G.3 |
| `renew-contracts` | `FinancialCronJobs.renewContracts()` (1er du mois) | G.3 |
| `expire-unanswered-resource-bookings` | `ResourceBookingCronJobs.expireUnansweredResourceBookings()` (15 min) | G.3 |

## 🟡 Edge Functions restantes (40) — à porter sprint G.5-bis ou V2

### Workflows métier (3)

| EF Supabase | Spring endpoint target | Priorité | Effort |
|-------------|-----------------------|----------|--------|
| `generate-invoices` | `POST /api/financial/invoices/generate-monthly` | Medium | 1j |
| `send-onboarding-decision` | `PATCH /api/store/onboarding/{id}/{accept\|reject}` | Low | 1j |
| `send-onboarding-request` | `POST /api/store/onboarding` | Low | 0.5j |

### AI Groq (4) — wrappers HTTP simples

| EF Supabase | Spring endpoint | Priorité | Effort |
|-------------|----------------|----------|--------|
| `oneclick-care-chat` | `POST /api/ai/care-chat` | High | 0.5j |
| `oneclick-ai-assistant` | `POST /api/ai/assistant` (avec rate limit /day) | High | 0.5j |
| `elite-ai-review` | `POST /api/ai/elite-review` | Medium | 0.5j |
| `generate-plan` | `POST /api/ai/plan` | Low | 0.5j |

Pattern senior : 1 service `core/ai/GroqClient` partagé + 1 endpoint par usage.

### Intégrations externes (3)

| EF Supabase | Spring endpoint | Priorité | Effort |
|-------------|----------------|----------|--------|
| `fetch-google-places` | `POST /api/restaurants/{id}/enrich-google-places` | Medium | 1j |
| `monitor-telemetry` | `POST /api/audit/telemetry` (déjà partiel via audit_log) | Low | 0.5j |
| `health-check` | `GET /actuator/health` (déjà Spring native) ✅ | — | DONE |

### Push notifications custom (5)

| EF Supabase | Spring endpoint | Priorité | Effort |
|-------------|----------------|----------|--------|
| `send-friend-request-push` | `POST /api/notifications/push/community` (générique) | Medium | 0.3j |
| `send-pcc-feedback-thread` | Idem via type='community' | Low | 0.2j |
| `send-pcc-feedback-resolved` | Idem | Low | 0.2j |
| `send-pcc-enrollment-invite` | Email via Resend wrapper | Medium | 0.5j |
| `send-homu-enrollment-invite` | Idem | Medium | 0.5j |

### Whitelabel Resend (déjà via API Resend HTTP — pattern à confirmer)

Pour les emails brandés (PCC gold, HOMU sakura, etc.), un service `core/email/ResendClient`
peut être créé. Pour V1, le frontend peut appeler directement Resend via clé API
restreinte (déjà le cas dans le legacy).

### Edge Functions à scope spécifique (25+)

- `search-restaurant` → déjà via `/api/search/restaurants` (ES) ✅
- `expire-unanswered-resa-pcc` → fusionné dans ResourceBookingCronJobs ✅
- Et ~22 autres EFs spécifiques (export, debug, internal tools) — port one-by-one selon besoin métier réel.

## 🎯 Stratégie de port progressif

**Pour chaque EF restante** :
1. Vérifier qu'elle est encore appelée par le frontend (`grep "supabase.functions.invoke"`)
2. Si oui → identifier l'endpoint Spring cible (souvent dans un module existant)
3. Créer service method + controller endpoint + tests intégration
4. Adapter le frontend pour appeler Spring au lieu de Supabase
5. Une fois 0 appels Supabase pour cette EF → la supprimer côté Supabase

## 🚫 Edge Functions à ne PAS porter (V1)

- EFs admin debug/internal jamais appelées par le frontend production
- EFs de hooks DB pg_net (qui devraient être @Scheduled ou triggers DB pur)
- EFs whitelabel one-off (PCC démo, HOMU custom features) — restent côté Supabase tant que la migration des données legacy n'est pas finalisée (Sprint G.7 ETL)
