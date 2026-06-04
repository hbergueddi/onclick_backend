-- ════════════════════════════════════════════════════════════════════
-- V65 — Allergènes du user (profil client) + visibilité staff sur la résa
-- ════════════════════════════════════════════════════════════════════
-- Parité legacy : la table `profiles` historique portait une colonne array
-- simple `allergens text[]` (14 allergènes EU UE, pas de table dédiée, pas de
-- RPC). Le client édite ses allergènes depuis « Informations personnelles »
-- (Pocket) ; le staff les voit sur la fiche réservation (read-view native qui
-- JOIN déjà `users` pour le prénom / téléphone du client).
--
--   COLONNE — users.allergens text[] NOT NULL DEFAULT '{}'
--     Liste de slugs d'allergènes (codes EN : gluten, lupin, lactose, eggs,
--     peanuts, tree_nuts, sesame, soy, fish, shellfish, molluscs, celery,
--     mustard, sulfites). NOT NULL + DEFAULT tableau vide → aucun backfill
--     nécessaire sur les comptes existants, et le mapping entité ne voit jamais
--     NULL (cohérent avec Restaurant.tags). Persisté via PATCH /api/users/me
--     (UPDATE:PROFILE) — pas de nouvelle autorité, réutilise le self-service.
--
--   INDEX — GIN partiel sur allergens (array_length > 0)
--     Calque le pattern des index GIN sur colonnes array. Partiel : seules les
--     lignes avec au moins un allergène sont indexées (la grande majorité des
--     comptes a un tableau vide), ce qui garde l'index petit. Utile si on
--     filtre un jour « clients ayant l'allergène X » côté analytics.
--
-- Idempotent (IF NOT EXISTS) — re-jouable sans erreur cross-environnement.
-- ddl-auto=validate : la colonne users.allergens DOIT exister pour que l'entité
-- User valide au boot — cette migration est donc un prérequis du champ entité.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS allergens text[] NOT NULL DEFAULT '{}'::text[];

CREATE INDEX IF NOT EXISTS idx_users_allergens
    ON users USING GIN (allergens)
    WHERE array_length(allergens, 1) > 0;

COMMENT ON COLUMN users.allergens IS
    'V65 — allergènes du user (profil client, parité legacy profiles.allergens). Slugs EU (gluten, lactose, …). Éditable via PATCH /api/users/me (UPDATE:PROFILE). Visible par le staff sur la fiche réservation (read-view).';
