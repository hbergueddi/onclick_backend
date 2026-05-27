-- V49 — wallet_admin_rate par contrat.
--
-- Le KPI "Admin Wallet" (PulsePro + OneClickHI Pro / Facturation) doit dériver du
-- contrat, comme le legacy partner_contracts.wallet_admin_rate qui n'avait pas été
-- migré (le Contract Spring ne portait que commission_rate). Sans cette colonne, le
-- front retombait toujours sur le défaut 2 % codé en dur.
--
-- Défaut 2.00 = constante plateforme historique → backfill non-destructif des
-- contrats existants. Idempotent (ADD COLUMN IF NOT EXISTS).
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS wallet_admin_rate numeric(5, 2) NOT NULL DEFAULT 2.00
        CHECK (wallet_admin_rate >= 0 AND wallet_admin_rate <= 100);

COMMENT ON COLUMN contracts.wallet_admin_rate IS 'Taux (%) reversé au wallet admin OneClick — par contrat (défaut 2.00)';
