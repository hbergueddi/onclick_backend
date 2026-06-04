-- ════════════════════════════════════════════════════════════════════
-- V64 — Ville du user (PersonalInfo) + quotas sociaux (import contacts)
-- ════════════════════════════════════════════════════════════════════
-- Deux ajouts indépendants livrés ensemble (2 items du sprint) :
--
--   ITEM 1 — users.city
--     Champ d'adresse léger éditable depuis la page « Informations personnelles »
--     (Pocket), aux côtés de first_name / last_name / phone / avatar_url. Persisté
--     via PATCH /api/users/me (UPDATE:PROFILE) — pas de nouvelle autorité. NULLABLE :
--     champ optionnel, aucun backfill nécessaire sur les ~167 comptes existants.
--
--   ITEM 2 — table contact_imports (quota import contacts 10/jour/user)
--     1 ligne = 1 import de carnet d'adresses déclenché par un user. Le service
--     social compte les imports du user sur les dernières 24 h ; au-delà de 10 il
--     rejette (429). Refs par UUID PLAT (user_id) — module social CLOSED, pas de
--     @ManyToOne cross-module (même convention que offer_reads V63 / friendships).
--     count = nombre de contacts soumis dans le batch (observabilité / debug).
--
--   ITEM 2.bis — index dédup ticket support « friends_cap »
--     Le plafond de 50 amis (enforcé côté SocialService) permet d'ouvrir un ticket
--     support catégorie 'friends_cap', dédupliqué 1/24 h par user. La dédup est
--     une lecture (opened_by, category, created_at) dans SupportService ; on ajoute
--     un index ciblé pour la rendre efficace sans scanner toute la table tickets.
--
-- Idempotent (IF NOT EXISTS) — re-jouable sans erreur cross-environnement.
-- ddl-auto=validate : la colonne users.city DOIT exister pour que l'entité User
-- valide au boot — cette migration est donc un prérequis du champ entité.
-- ════════════════════════════════════════════════════════════════════

-- ─── ITEM 1 : colonne city sur users ────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS city VARCHAR(128);

COMMENT ON COLUMN users.city IS
    'V64 — ville du user (PersonalInfo Pocket). Éditable via PATCH /api/users/me (UPDATE:PROFILE). Optionnel (nullable).';

-- ─── ITEM 2 : table contact_imports (quota 10/jour/user) ─────────────────────
-- contact_count : taille du batch soumis lors de l'import (≥ 0). Pas de PII des
-- contacts stockée ici (RGPD) : seul le fait + le volume de l'import est tracé.
CREATE TABLE IF NOT EXISTS contact_imports (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_count  INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Comptage des imports d'un user sur une fenêtre glissante (quota 10/24 h) :
-- WHERE user_id = ? AND created_at > now() - interval '24h'. Index composite ciblé.
CREATE INDEX IF NOT EXISTS idx_contact_imports_user_created
    ON contact_imports(user_id, created_at DESC);

COMMENT ON TABLE contact_imports IS
    'V64 — journal des imports de contacts (carnet d''adresses → matching OneClick). Sert au quota 10/jour/user enforcé par SocialService. Aucune PII des contacts stockée.';

-- ─── ITEM 2.bis : index dédup ticket support « friends_cap » ─────────────────
-- Accélère la vérif « existe-t-il un ticket friends_cap ouvert par ce user dans
-- les 24 h ? » (SupportService.createFriendsCapTicket). Partiel sur la catégorie
-- pour rester petit (un seul type de ticket concerné par la dédup).
CREATE INDEX IF NOT EXISTS idx_support_tickets_opener_cap
    ON support_tickets(opened_by, created_at DESC)
    WHERE category = 'friends_cap';
