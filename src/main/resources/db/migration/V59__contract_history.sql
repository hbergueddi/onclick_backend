-- ════════════════════════════════════════════════════════════════════
-- V59 — contract_history : journal d'audit des modifications de contrat
-- ════════════════════════════════════════════════════════════════════
--
-- Le panneau admin ContractHistoryPanel (/galaxy/contrats) affiche une timeline
-- des changements de champs d'un contrat (ancienne valeur → nouvelle valeur, par
-- qui, quand). La table n'existait pas côté Spring → lecture morte. On la crée et
-- on l'alimente sur chaque PATCH de contrat (diff best-effort des champs suivis :
-- statut, commissions, échéance, renouvellement auto, conditions de paiement).
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS contract_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    field_name  VARCHAR(64) NOT NULL,
    old_value   VARCHAR(256),
    new_value   VARCHAR(256),
    changed_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contract_history_contract
    ON contract_history(contract_id, created_at DESC);
