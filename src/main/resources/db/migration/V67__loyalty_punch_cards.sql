-- ════════════════════════════════════════════════════════════════════
-- V67 — Cartes de fidélité « punch cards » 10/1 (PCC Lot 3)
-- ════════════════════════════════════════════════════════════════════
-- Carte de fidélité PCC : à chaque réservation ressource HONORÉE
-- (resource_booking status → 'completed'), l'organisateur gagne +1 « punch »
-- sur la carte de l'activité correspondante. À 10 punches (threshold), il a droit
-- à 1 séance gratuite (redeemed par le staff). 1 carte par (tenant, client, activité).
--
-- Le punch est appliqué par le module loyalty (ResourceBookingPunchListener) qui
-- réagit à l'event shared ResourceBookingStatusChangedEvent publié par le module
-- resource_booking. Frontière Modulith respectée (aucune dépendance directe).
--
-- Modèle GÉNÉRIQUE vs legacy PCC Supabase (loyalty_punch_cards.activity_type +
-- trigger SQL) : ici la colonne s'appelle simplement « activity » et la logique
-- (palier, redeem) vit dans PunchCardService (Java), pas dans un trigger.
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:PUNCH_CARDS')") partout
-- (jamais isAuthenticated/hasRole). Le self-scope (le CLIENT voit SES cartes) +
-- l'ABAC staff-only du redeem sont gérés dans PunchCardService.
--
-- Grants :
--   • CLIENT                                       → VIEW:PUNCH_CARDS
--       (le membre consulte SES cartes — self-scope forcé côté service)
--   • STAFF + RESTAURATEUR + GROUP_ADMIN
--     + SUPERADMIN                                 → VIEW + UPDATE:PUNCH_CARDS
--       (le staff applique une séance gratuite = redeem, via UPDATE)
--
-- Aucun rôle ne reçoit CREATE/DELETE:PUNCH_CARDS : une carte naît et grandit via
-- l'event (auto-punch), elle ne se crée ni ne se supprime à la main. Pas d'over-grant.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Table loyalty_punch_cards ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS loyalty_punch_cards (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL,
    client_id        uuid        NOT NULL,
    activity         varchar(40) NOT NULL,
    count_punched    int         NOT NULL DEFAULT 0,
    threshold        int         NOT NULL DEFAULT 10,
    redeemed_count   int         NOT NULL DEFAULT 0,
    last_punched_at  timestamptz,
    last_redeemed_at timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_punch_card_tenant_client_activity UNIQUE (tenant_id, client_id, activity)
);

-- Lecture « mes cartes » filtrée par (tenant, client).
CREATE INDEX IF NOT EXISTS idx_punch_cards_tenant_client
    ON loyalty_punch_cards (tenant_id, client_id);

-- ── 2. Ressource RBAC PUNCH_CARDS (feuille sous la catégorie Fidélité) ───────
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'PUNCH_CARDS', 'Cartes de fidélité (punch 10/1)',
       (SELECT id FROM menus WHERE code = 'FIDELITE'), 35
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'PUNCH_CARDS');

-- ── 3. CLIENT → VIEW (voit SES cartes) ───────────────────────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'PUNCH_CARDS'
  AND a.code IN ('VIEW')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 4. STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN → VIEW + UPDATE (redeem) ──
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'PUNCH_CARDS'
  AND a.code IN ('VIEW', 'UPDATE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
