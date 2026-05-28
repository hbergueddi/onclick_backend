-- ════════════════════════════════════════════════════════════════════
-- V56 — Articles désactivés par contrat (override par contrat)
-- ════════════════════════════════════════════════════════════════════
-- Un contrat hérite des articles (clauses) de son template, mais l'admin peut
-- désactiver certains articles pour un contrat donné (aperçu PDF /galaxy/contrats).
-- Remplace le shim supabase mort sur contract_disabled_articles.
--
-- Surrogate id (JPA simple) + UNIQUE(contract_id, article_id). CASCADE des 2 côtés.
-- Idempotent.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS contract_disabled_articles (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id uuid        NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    article_id  uuid        NOT NULL REFERENCES contract_template_articles(id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (contract_id, article_id)
);

CREATE INDEX IF NOT EXISTS idx_contract_disabled_articles_contract
    ON contract_disabled_articles(contract_id);
