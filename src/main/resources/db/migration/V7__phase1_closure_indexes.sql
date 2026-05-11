-- ============================================================================
-- V7 — Phase 1 closure : index manquant §18 spec senior dev
-- ============================================================================
-- Référence : oneclick_architecture_enterprise_optimized.md §18
--   "INDEX(account_id, expires_at)"
--
-- Cas d'usage : afficher au client "vos X points expirent dans Y jours"
-- (par compte loyalty + tri ASC sur expires_at) → soutient l'écran wallet pocket.
--
-- Index PARTIEL : on n'indexe que les transactions qui ont une date d'expiration
-- (type='earn' avec expires_at non null) — les redemptions/adjustments n'ont
-- jamais de TTL, donc on évite des entrées d'index inutiles (~50% des rows).
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_loyalty_transactions_account_expires
    ON loyalty_transactions (account_id, expires_at ASC)
    WHERE expires_at IS NOT NULL;

COMMENT ON INDEX idx_loyalty_transactions_account_expires IS
    'Spec §18 : INDEX(account_id, expires_at). Sert le query "mes points qui expirent" sur l''écran wallet client.';
