-- V102 — Aligne la pénalité no-show sur le legacy : un no-show coûte 0.5 (et non 0.1).
-- (Renommée V100 → V102 : V100/V101 déjà occupés par restaurant_referral / grant_media_to_scanning_roles.)
--
-- Décision produit (2026-06-20) : un compte neuf démarre à score_initial = 5.0 (badge « Nouveau »
-- tant qu'aucune réservation notée). Après la 1re réservation :
--   • honorée  → +gain_par_palier (0.1), plafonné à 5.0  ⇒ reste 5.0
--   • no_show  → -penalite_no_show (0.5)                  ⇒ passe à 4.5
--
-- Seule la pénalité change ; score_initial (5.0), gain_par_palier (0.1) et les bornes [0 ; 5.0]
-- restent inchangés. N'édite PAS V52 (déjà appliquée) — migration corrective dédiée.
--
-- Idempotent + non destructif : ne met à jour que les lignes encore au défaut historique 0.1
-- (ne réécrit pas une valeur déjà personnalisée par un admin via l'écran « Scoring »). Après
-- exécution la valeur vaut 0.5 et ne re-matche plus la clause WHERE.
UPDATE client_score_config
SET penalite_no_show = 0.5,
    updated_at = NOW()
WHERE penalite_no_show = 0.1;
