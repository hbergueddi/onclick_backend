-- V52 — configuration du moteur de notation client (/reservations · onglet Scoring).
--
-- L'écran admin "Notation client — Paramètres" éditait une ligne singleton
-- client_score_config (seuils de classification + règles de calcul du score de
-- fiabilité). Aucun domaine backend n'existait → l'écran restait sur le shim
-- Supabase mort (lecture client_score_config → []).
--
-- Singleton : une seule ligne (seed par défaut ci-dessous). Idempotent.
CREATE TABLE IF NOT EXISTS client_score_config (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    min_reservations       integer       NOT NULL DEFAULT 3,
    seuil_excellent        numeric(5,2)  NOT NULL DEFAULT 95,
    seuil_fiable           numeric(5,2)  NOT NULL DEFAULT 80,
    seuil_moyen            numeric(5,2)  NOT NULL DEFAULT 60,
    score_initial          numeric(3,1)  NOT NULL DEFAULT 5.0,
    penalite_no_show       numeric(3,1)  NOT NULL DEFAULT 0.1,
    honorees_pour_remonter integer       NOT NULL DEFAULT 5,
    gain_par_palier        numeric(3,1)  NOT NULL DEFAULT 0.1,
    fenetre_mois           integer       NOT NULL DEFAULT 6,
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE client_score_config IS 'Singleton — paramètres du moteur de notation client (fiabilité).';

-- Seed de la ligne par défaut (UUID fixe) si la table est vide.
INSERT INTO client_score_config (id)
SELECT 'c1000000-0000-0000-0000-000000000001'
WHERE NOT EXISTS (SELECT 1 FROM client_score_config);
