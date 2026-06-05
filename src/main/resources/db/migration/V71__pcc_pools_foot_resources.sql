-- ════════════════════════════════════════════════════════════════════
-- V71 — Ressources bookables PCC : Piscines + Foot (tenant palmeraie)
-- ════════════════════════════════════════════════════════════════════
-- Ajoute 3 modules réservables au parc « resources » du tenant palmeraie, en
-- réutilisant l'infra resource_booking existante (Padel/Tennis/Spa/Golf). 100%
-- seed SQL : AUCUNE logique service, AUCUN nouveau grant RBAC.
--
--   1. Piscine intérieure       — resource_type 'indoor_pool'    (1 bassin)
--   2. Piscine semi-olympique   — resource_type 'olympic_pool'   (1 bassin)
--   3. Foot                     — resource_type 'football_field' (5 terrains 5v5)
--
-- POURQUOI ce seed (et pas un CHECK/enum à étendre) :
--   La colonne resources.resource_type est un `text` LIBRE (cf V5) — aucun CHECK,
--   aucun type enum ne la contraint. Ajouter un nouveau type = simplement insérer
--   des lignes. Il n'y a donc RIEN à étendre côté contrainte (DO $$ … duplicate_object
--   inutile ici). On calque exactement le pattern des ressources palmeraie déjà en
--   base : UUID déterministes, capacity renseignée, PAS de resource_pricings (le
--   modèle PCC ne tarifie pas ces ressources — booking sans pricing_id, cf
--   ResourceBookingService.createBooking où pricingId est optionnel).
--
-- RBAC : INCHANGÉ. La gestion du parc reste {VERB}:RESOURCE_BOOKINGS (V32/V66) et
--   le workflow membre {VERB}:BOOKINGS (V66). Le CLIENT garde VIEW:RESOURCE_BOOKINGS
--   pour lister + CREATE:BOOKINGS pour réserver. Pas de nouveau menu/permission →
--   pas de flush redis nécessaire (aucune autorité ajoutée).
--
-- IDEMPOTENCE : tous les INSERT sont gardés par NOT EXISTS sur l'UUID cible. Ré-
--   exécution sûre. Sur la DB enterprise locale, les 5 'football_field' (Terrain 1..5)
--   ont déjà été seedés out-of-band avec ces MÊMES UUID déterministes → l'INSERT est
--   un no-op pour eux (présence garantie), et crée les piscines manquantes. Sur une DB
--   fraîche (CI, Flyway V1+), V71 crée les 8 lignes.
--
-- ANTI-RÉGRESSION : ressources scopées tenant palmeraie uniquement. 0 impact
--   OneClick/HOMU/Restopro.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Piscine intérieure (chauffée toute l'année) — 1 bassin, type 'indoor_pool' ──
INSERT INTO resources (id, tenant_id, resource_type, name, description, capacity, enabled)
SELECT '00007001-7001-4000-8000-000000000701'::uuid,
       t.id, 'indoor_pool', 'Piscine intérieure',
       'Bassin intérieur chauffé toute l''année.', 30, true
FROM tenants t
WHERE t.slug = 'palmeraie'
  AND NOT EXISTS (SELECT 1 FROM resources r WHERE r.id = '00007001-7001-4000-8000-000000000701'::uuid);

-- ── 2. Piscine semi-olympique — 1 bassin, type 'olympic_pool' ───────────────────
INSERT INTO resources (id, tenant_id, resource_type, name, description, capacity, enabled)
SELECT '00007002-7002-4000-8000-000000000702'::uuid,
       t.id, 'olympic_pool', 'Piscine semi-olympique',
       'Bassin semi-olympique, lignes de nage et grande plage.', 60, true
FROM tenants t
WHERE t.slug = 'palmeraie'
  AND NOT EXISTS (SELECT 1 FROM resources r WHERE r.id = '00007002-7002-4000-8000-000000000702'::uuid);

-- ── 3. Foot — 5 terrains 5v5, type 'football_field', capacité 10 joueurs/terrain ──
--    UUID déterministes alignés sur les rangs déjà présents en base enterprise
--    (0000601f-5555-4000-8000-0000000005xx) → no-op si déjà seedés, créés sinon.
INSERT INTO resources (id, tenant_id, resource_type, name, description, capacity, enabled)
SELECT v.id::uuid, t.id, 'football_field', v.name,
       'Terrain de football 5v5.', 10, true
FROM tenants t
CROSS JOIN (VALUES
    ('0000601f-5555-4000-8000-000000000501', 'Terrain 1'),
    ('0000601f-5555-4000-8000-000000000502', 'Terrain 2'),
    ('0000601f-5555-4000-8000-000000000503', 'Terrain 3'),
    ('0000601f-5555-4000-8000-000000000504', 'Terrain 4'),
    ('0000601f-5555-4000-8000-000000000505', 'Terrain 5')
) AS v(id, name)
WHERE t.slug = 'palmeraie'
  AND NOT EXISTS (SELECT 1 FROM resources r WHERE r.id = v.id::uuid);
