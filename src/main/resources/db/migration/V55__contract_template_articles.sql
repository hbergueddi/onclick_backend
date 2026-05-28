-- ════════════════════════════════════════════════════════════════════
-- V55 — Articles (clauses) des templates contractuels
-- ════════════════════════════════════════════════════════════════════
-- Le modèle Spring `contract_templates` portait un corps unique (body). La page
-- admin /galaxy/contrats (onglet Clauses + aperçu PDF) gère un template comme une
-- LISTE d'articles numérotés (clauses) — concept legacy contract_template_articles.
-- On crée le domaine pour permettre la migration du shim supabase mort.
--
-- Idempotent. CASCADE sur suppression du template parent.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS contract_template_articles (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id    uuid         NOT NULL REFERENCES contract_templates(id) ON DELETE CASCADE,
    article_number integer      NOT NULL,
    title          varchar(256) NOT NULL,
    content        text         NOT NULL,
    sort_order     integer      NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contract_template_articles_template
    ON contract_template_articles(template_id);
