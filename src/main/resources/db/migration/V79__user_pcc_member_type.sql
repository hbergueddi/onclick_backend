-- V79 — Type de membre PCC (axe H — usePccMemberType / remise résidant·non-résidant).
--
-- Parité legacy profiles.pcc_member_type (sprint 14/05 : remise -20% résidants,
-- -15% non-résidants). Posé par un admin (PATCH /api/users/{id}/pcc-member-type,
-- UPDATE:USERS) ; lu partout via UserDto (/me + /{id}). NULL = pas un membre PCC.
-- Idempotent (IF NOT EXISTS), nullable, CHECK sur le vocabulaire.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pcc_member_type varchar(32);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_pcc_member_type'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_pcc_member_type
            CHECK (pcc_member_type IS NULL OR pcc_member_type IN ('resident', 'non_resident'));
    END IF;
END $$;

COMMENT ON COLUMN users.pcc_member_type IS
    'H — type de membre PCC (resident|non_resident) pour la remise ; NULL = non-membre. Posé par admin.';
