-- V107 — Lot 4b : champs « RuleBuilder » legacy sur gain_rules (parité 1:1 éditeur fidélité).
--
-- Le RuleBuilderPanel legacy (4Click) capturait, en plus du taux/min/plafonds déjà présents :
--   • point_value_mad        — valeur monétaire d'un point configurée dans la règle (override)
--   • eval_period_type/value  — période d'évaluation (week|month × N)
--   • benefit_duration_days   — durée du bénéfice (jours)
--   • min_spend_monthly       — seuil de dépense mensuel d'activation
--
-- Tous ADDITIFS + NULLABLE → aucune règle existante n'est impactée, et le moteur de gain
-- (conversion_rate × montant, plafonné) reste inchangé. Ces champs sont stockés/round-trippés
-- par l'éditeur (comme en legacy) ; leur consommation par le moteur reste hors scope (additif).
ALTER TABLE gain_rules
    ADD COLUMN IF NOT EXISTS point_value_mad       NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS eval_period_type      VARCHAR(8),
    ADD COLUMN IF NOT EXISTS eval_period_value     INTEGER,
    ADD COLUMN IF NOT EXISTS benefit_duration_days INTEGER,
    ADD COLUMN IF NOT EXISTS min_spend_monthly     NUMERIC(12,2);

-- Garde-fous d'intégrité (compatibles avec les lignes existantes = toutes NULL).
ALTER TABLE gain_rules
    ADD CONSTRAINT chk_gain_rules_eval_period_type
        CHECK (eval_period_type IS NULL OR eval_period_type IN ('week', 'month')),
    ADD CONSTRAINT chk_gain_rules_eval_period_value
        CHECK (eval_period_value IS NULL OR eval_period_value > 0),
    ADD CONSTRAINT chk_gain_rules_benefit_duration
        CHECK (benefit_duration_days IS NULL OR benefit_duration_days > 0),
    ADD CONSTRAINT chk_gain_rules_point_value_mad
        CHECK (point_value_mad IS NULL OR point_value_mad >= 0),
    ADD CONSTRAINT chk_gain_rules_min_spend_monthly
        CHECK (min_spend_monthly IS NULL OR min_spend_monthly >= 0);

COMMENT ON COLUMN gain_rules.point_value_mad IS 'Lot 4b — override valeur du point (MAD) configuré par le RuleBuilder ; NULL = valeur résolue depuis loyalty_rules.point_value.';
COMMENT ON COLUMN gain_rules.eval_period_type IS 'Lot 4b — type de période d''évaluation RuleBuilder (week|month).';
COMMENT ON COLUMN gain_rules.eval_period_value IS 'Lot 4b — nombre de périodes d''évaluation (RuleBuilder).';
COMMENT ON COLUMN gain_rules.benefit_duration_days IS 'Lot 4b — durée du bénéfice en jours (RuleBuilder).';
COMMENT ON COLUMN gain_rules.min_spend_monthly IS 'Lot 4b — seuil de dépense mensuel d''activation (MAD, RuleBuilder).';
