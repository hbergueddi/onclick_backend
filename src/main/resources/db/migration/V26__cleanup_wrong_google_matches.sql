-- ════════════════════════════════════════════════════════════════════
-- V26 — Cleanup wrong Google Places matches (Sprint K hot fix)
-- ════════════════════════════════════════════════════════════════════
--
-- Suite à l'enrichissement Google V25, 2 restos ont récupéré des place_id
-- erronés parce que le seed initial avait une adresse incohérente avec la
-- ville déclarée :
--
--  1. Azar Casablanca → seed address "Rue de Yougoslavie، Marrakech 40000"
--     → Google search "Azar Casablanca" retourne le Azar de Marrakech
--     (place_id ChIJvbUsXQPLpw0R6yJ48EH_lcw). Resto Casablanca probablement
--     fictif (jamais existé) ou fermé.
--
--  2. El Rincón Andaluz Tétouan → seed address "Eivissa, Illes Balears, Espagne"
--     → Google search retourne un resto de Espagne (place_id
--     ChIJJawRffNFCw0RFdwbyerfVtQ). Resto Tétouan probablement fermé/déplacé.
--
-- Retry avec re-search forcée donne le même mauvais résultat (Google retourne
-- le top match qui est l'autre place). User demande : laisser vide plutôt que
-- afficher data erronée.
--
-- Le Gharb Kénitra n'a pas besoin de fix (déjà NULL — no_match Google).

BEGIN;

UPDATE restaurants
   SET google_place_id = NULL,
       google_rating = NULL,
       google_reviews_count = NULL,
       google_updated_at = NULL,
       opening_hours = NULL,
       cuisine = NULL,
       budget = NULL,
       tags = '{}',
       latitude = NULL,
       longitude = NULL,
       address = NULL,
       phone = NULL,
       website_url = NULL,
       updated_at = NOW()
 WHERE (name = 'Azar' AND city = 'Casablanca')
    OR (name = 'El Rincón Andaluz' AND city = 'Tétouan');

COMMIT;
