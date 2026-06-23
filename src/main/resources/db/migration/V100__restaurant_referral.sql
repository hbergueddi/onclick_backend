-- ════════════════════════════════════════════════════════════════════════
-- V100 — Parrainage RESTAURANT-à-RESTAURANT (owner → owner)
-- ════════════════════════════════════════════════════════════════════════
-- Un restaurateur (owner d'un resto EXISTANT) parraine un NOUVEAU resto. À
-- l'activation du code (immédiat), le PARRAIN seul reçoit des POINTS de
-- fidélité (le filleul ne reçoit rien ; aucun flux financier/commission).
--
-- Scope : MONO-TENANT « oneclick » (pas cross-tenant), PAS de plafond.
--
-- Modèle DB isolé de `referrals` (parrainage CLIENT, module social) : un
-- parrainage resto porte un resto parrain + (à l'activation) un resto filleul,
-- et matérialise la récompense (reward_points/rewarded_at). Le crédit de points
-- est appliqué par le module loyalty via l'event RestaurantReferralActivatedEvent
-- (frontière Modulith — pas d'accès direct loyalty depuis ce module).
--
-- Endpoints (RBAC v2 strict hasAuthority('VERB:RESTAURANT_REFERRAL')) :
--   GET  /api/restaurant-referrals/my-code?restaurantId=  (VIEW)
--   POST /api/restaurant-referrals/activate {code, refereeRestaurantId} (CREATE)
--   GET  /api/restaurant-referrals/mine?restaurantId=     (VIEW)
-- Le gate fin reste l'ABAC owner-scope côté service (owner du resto concerné).

-- ─── 1) Table ──────────────────────────────────────────────────────────────
CREATE TABLE restaurant_referrals (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_restaurant_id  uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    referrer_user_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referral_code           varchar(64) NOT NULL UNIQUE,
    referee_restaurant_id   uuid REFERENCES restaurants(id) ON DELETE SET NULL,
    referee_user_id         uuid REFERENCES users(id) ON DELETE SET NULL,
    status                  varchar(32) NOT NULL DEFAULT 'pending'
                                CHECK (status IN ('pending', 'activated')),
    activated_at            timestamptz,
    reward_points           integer NOT NULL DEFAULT 0,
    rewarded_at             timestamptz,
    tenant_id               uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE restaurant_referrals IS
    'Parrainage restaurant→restaurant (owner→owner). Récompense = points fidélité au parrain seul, à l''activation. Mono-tenant oneclick.';

-- ─── 2) Index ──────────────────────────────────────────────────────────────
-- (referral_code) déjà couvert par la contrainte UNIQUE (index implicite) — pas
-- de doublon d'index. On indexe les colonnes de filtrage des lectures owner-scope.
CREATE INDEX idx_restaurant_referrals_referrer_restaurant
    ON restaurant_referrals (referrer_restaurant_id);
CREATE INDEX idx_restaurant_referrals_referee_restaurant
    ON restaurant_referrals (referee_restaurant_id)
    WHERE referee_restaurant_id IS NOT NULL;
CREATE INDEX idx_restaurant_referrals_tenant
    ON restaurant_referrals (tenant_id);

-- ─── 3) RBAC — ressource RESTAURANT_REFERRAL + menu + grants ────────────────
-- Calque V94 (pattern idempotent NOT EXISTS). Authority = action.code + ':' + menu.code
-- (cf UserRoleAuthoritiesConverter) → CREATE/VIEW/UPDATE:RESTAURANT_REFERRAL.

-- 3a) Menu/ressource (sous ADMINISTRATION). id/created_at/updated_at : defaults DB.
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'RESTAURANT_REFERRAL', 'Parrainage restaurant',
       (SELECT id FROM menus WHERE code = 'ADMINISTRATION'), 80
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'RESTAURANT_REFERRAL');

-- 3b) Grants CREATE/VIEW/UPDATE aux rôles « owner de resto » et admins.
--   - RESTAURATEUR : owner qui parraine / active / consulte (cas nominal).
--   - GROUP_ADMIN / SUPERADMIN : admins multi-resto / plateforme (dashboard).
-- Le GATE réel reste l'ABAC owner-scope côté service (owner du resto concerné).
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'RESTAURANT_REFERRAL'
  AND a.code IN ('CREATE', 'VIEW', 'UPDATE')
  AND NOT EXISTS (
      SELECT 1 FROM permissions p
      WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
