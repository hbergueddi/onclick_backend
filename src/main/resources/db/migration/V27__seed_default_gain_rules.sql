-- ════════════════════════════════════════════════════════════════════
-- V27 — Seed default gain_rules pour les 1035 restos sans règle
-- ════════════════════════════════════════════════════════════════════
--
-- Symptôme : page /prodesk/regles-credit affiche 404 pour 100% des restos
-- (GET /api/loyalty/gain-rules/by-restaurant/{id} → 404 NotFoundException).
--
-- Cause data : seed legacy avait des gain_rules pour les 20 restos initiaux,
-- la migration vers Spring n'a pas backfillé les 1035 restos `active` sans
-- règle. Owner ne peut pas tester snap2earn sans une règle (FK loyalty).
--
-- Valeurs par défaut (cohérent avec column defaults DB + standard OneClick) :
--   conversion_rate = 0.10  (1 point = 10 MAD dépensés)
--   min_amount      = 0     (pas de minimum)
--   cap_per_visit   = NULL  (pas de plafond par visite)
--   cap_per_month   = NULL  (pas de plafond mensuel)
--   is_active       = true
--
-- Le owner peut surcharger via PATCH /api/loyalty/gain-rules/{id} ensuite.
--
-- Idempotent : INSERT ... ON CONFLICT DO NOTHING via la UNIQUE constraint
-- gain_rules_restaurant_unique. Re-runnable sans risque.

BEGIN;

INSERT INTO gain_rules (
    restaurant_id, tenant_id, conversion_rate, min_amount, is_active
)
SELECT
    r.id, r.tenant_id, 0.10, 0, true
  FROM restaurants r
 WHERE r.status = 'active'
   AND r.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM gain_rules gr
       WHERE gr.restaurant_id = r.id AND gr.deleted_at IS NULL
   )
ON CONFLICT (restaurant_id) DO NOTHING;

COMMIT;
