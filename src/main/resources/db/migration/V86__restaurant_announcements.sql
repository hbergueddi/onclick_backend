-- ─────────────────────────────────────────────────────────────────────────────
-- Gap #6 — restaurant_announcements 24h (port legacy 14/05/2026)
-- ─────────────────────────────────────────────────────────────────────────────
--
-- USE CASE : un owner/staff d'un restaurant publie un message court éphémère
-- (ex « Fermé ce dimanche », « Cuisine ouverte jusqu'à minuit »). Le message
-- apparaît sur la fiche restaurant (spotlight membre) sous « Réserver une table »
-- et disparaît automatiquement 24h après publication.
--
-- Distinct de `tenant_announcements` (modules/announcement) : celui-ci est au niveau
-- TENANT (comm B2B descendante tenant-admin → staff). Ici c'est au niveau RESTAURANT
-- (staff resto → membre/client), éphémère 24h, 1 seule active par resto.
--
-- ARCHITECTURE Spring (senior) : 0 trigger SQL. La logique « expires = created+24h »
-- et « 1 seule active par resto » vit dans RestaurantAnnouncementService (testable,
-- traçable) — cohérent avec la philosophie du module announcement.
--
-- SCOPE : générique (tous tenants). Utilisé d'abord par PCC (Le Resto/Kamoun),
-- mais HOMU/OneClick peuvent l'activer côté front au besoin.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS restaurant_announcements (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    message       VARCHAR(280) NOT NULL,
    author_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ra_message_not_blank CHECK (length(trim(message)) > 0),
    CONSTRAINT ra_expires_after_created CHECK (expires_at > created_at)
);

-- Fetch rapide de l'annonce active d'un resto (lecture : restaurant_id + expires_at > now).
CREATE INDEX IF NOT EXISTS idx_resto_announce_restaurant_active
    ON restaurant_announcements (restaurant_id, expires_at DESC);

COMMENT ON TABLE restaurant_announcements IS
    'Annonce éphémère 24h par restaurant (staff → membre, fiche spotlight). 1 active/resto. Gap #6 — port legacy 14/05. Logique expiry/single-active dans le service (0 trigger).';
COMMENT ON COLUMN restaurant_announcements.expires_at IS
    'created_at + 24h (forcé par RestaurantAnnouncementService). Lecture filtrée expires_at > now.';
