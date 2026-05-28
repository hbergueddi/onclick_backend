-- V53 — regroupement de campagne multi-restaurant sur les offres.
--
-- L'admin Promotions Lounge crée une campagne ciblant N restaurants → N offres.
-- Le frontend générait un campaign_id (UUID) commun mais le backend ne le
-- persistait pas (OfferDto/OfferCreateDto sans campaignId). On ajoute la colonne
-- pour conserver le lien de campagne (regroupement / dédup côté admin).
--
-- Idempotent (ADD COLUMN IF NOT EXISTS). Nullable : une offre isolée (1 resto)
-- n'a pas de campaign_id.
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS campaign_id uuid;

CREATE INDEX IF NOT EXISTS idx_offers_campaign ON offers(campaign_id) WHERE campaign_id IS NOT NULL;
