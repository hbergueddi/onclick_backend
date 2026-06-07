-- ============================================================
-- Gap #1 — Gain-rule bulk assignment (Forge)
-- ============================================================
-- Permet à l'admin plateforme d'assigner UNE règle globale
-- (loyalty_tier_rules) à N restaurants en masse. Le modèle Spring
-- conserve UNE gain_rule par restaurant (snap2earn lit gain_rules
-- en priorité) : "assigner" = recopier les champs de conversion de
-- la tier-rule source dans gain_rules.{restaurant} + tracer la source.
--
-- Équivalent legacy : restaurant_gain_rules.source_rule_id + RPCs
-- assign/unassign/sync/get_rule_assignments (migration 4Click
-- 20260419130000_rule_assignments.sql). Ici, la source est une
-- loyalty_tier_rule (= legacy gain_rules global) et la cible est
-- gain_rules (= legacy restaurant_gain_rules).
--
-- Aucune nouvelle ressource RBAC : réutilise LOYALTY_TIER (V43,
-- SUPERADMIN) — l'admin gère les règles plateforme pour tous les tenants.
-- ============================================================

-- FK optionnelle vers la tier-rule source (NULL = règle par-restaurant manuelle).
ALTER TABLE public.gain_rules
  ADD COLUMN IF NOT EXISTS source_tier_rule_id UUID
    REFERENCES public.loyalty_tier_rules(id) ON DELETE SET NULL;

-- Index pour get_rule_assignments / counts (filtrage par source).
CREATE INDEX IF NOT EXISTS idx_gain_rules_source_tier_rule
  ON public.gain_rules(source_tier_rule_id)
  WHERE source_tier_rule_id IS NOT NULL;
