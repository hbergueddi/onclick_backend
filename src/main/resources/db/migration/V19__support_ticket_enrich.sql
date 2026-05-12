-- ════════════════════════════════════════════════════════════════════
-- V19 — Sprint G.2.6 : enrichir support_tickets (port legacy → enterprise)
-- ════════════════════════════════════════════════════════════════════
-- Le schéma V6 de support_tickets était minimal. Le frontend (Pulse admin +
-- ProDesk support) attend en plus :
--   • restaurant_id   — pour filtrer support par resto + admin escalation
--   • photos          — URLs S3 jointes (preuves visuelles)
--   • internal        — ticket interne staff (vs externe client)
--   • escalated_to_admin — flag remontée admin (workflow Pulse/TrustWatch)
--   • last_reply      — dernier message (denormalisé pour list view fast)
--   • ai_handled / ai_summary — résumé Groq (oneclick-care-chat)
--
-- Migration additive — données existantes préservées (NULL pour nouveaux champs).
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE support_tickets
    ADD COLUMN restaurant_id      UUID REFERENCES restaurants(id) ON DELETE SET NULL,
    ADD COLUMN photos             TEXT[] DEFAULT '{}',
    ADD COLUMN internal           BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN escalated_to_admin BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN last_reply         TEXT,
    ADD COLUMN ai_handled         BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ai_summary         TEXT;

-- Index pour les filtres ProDesk (par restaurant) et Pulse (escalated only)
CREATE INDEX idx_support_tickets_restaurant
    ON support_tickets(restaurant_id, status, created_at DESC)
    WHERE restaurant_id IS NOT NULL;

CREATE INDEX idx_support_tickets_escalated
    ON support_tickets(escalated_to_admin, status, created_at DESC)
    WHERE escalated_to_admin = true;

CREATE INDEX idx_support_tickets_internal
    ON support_tickets(internal, status, created_at DESC)
    WHERE internal = true;

COMMENT ON COLUMN support_tickets.restaurant_id IS
    'Restaurant rattaché — null pour les tickets purement client (compte, app, etc.)';
COMMENT ON COLUMN support_tickets.escalated_to_admin IS
    'Flag remontée admin (workflow Pulse) — staff resto peut escalader vers admin platform';
COMMENT ON COLUMN support_tickets.ai_summary IS
    'Résumé AI (Groq llama 3.3 70B via oneclick-care-chat) — TL;DR du thread';
