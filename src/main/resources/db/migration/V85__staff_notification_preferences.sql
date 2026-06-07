-- ─────────────────────────────────────────────────────────────────────────────
-- Gap #5 — staff_notification_preferences (port legacy B.5 du 29/04/2026)
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Préférences de notifications par utilisateur (page /prodesk/staff-settings).
-- 1 row par user (UNIQUE), 5 toggles correspondant aux catégories de la page
-- StaffNotifications (booking, reservation, feedback, loyalty, system).
--
-- Le legacy stockait ces toggles côté Supabase ; le portage Spring les avait
-- temporairement dégradés en localStorage (gap documenté dans useStaffSettings.ts).
-- Cette migration restaure la persistance serveur.
--
-- Tenant-agnostic by design : aucune référence à tenant_id/restaurant_id.
-- L'utilisateur (staff de tout tenant) gère ses propres préférences ; le scope
-- est intrinsèque (self-service /me via JWT + colonne user_id UNIQUE).
--
-- V1 SCOPE (identique au legacy) : persistance + filtrage d'affichage côté FE.
-- Les producteurs de notifications continuent d'émettre toujours ; le respect
-- des préférences à l'émission est différé en V2 (les 7 types canoniques Spring
-- — reservation/loyalty/promotion/community/support/system/announcement — ne
-- mappent pas 1:1 sur les 5 catégories legacy : filtrer à l'émission
-- supprimerait des notifications légitimes — risque de correction).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS staff_notification_preferences (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    -- Toggles par catégorie (tous true par défaut → opt-out)
    booking      BOOLEAN      NOT NULL DEFAULT true,  -- résa activités (PCC Padel/Spa/Golf/etc + séminaires)
    reservation  BOOLEAN      NOT NULL DEFAULT true,  -- résa restos (Le Resto, Kamoun, OneClick standard)
    feedback     BOOLEAN      NOT NULL DEFAULT true,  -- avis membres (feedback)
    loyalty      BOOLEAN      NOT NULL DEFAULT true,  -- points & fidélité (loyalty, points, tier_upgrade, promo)
    system       BOOLEAN      NOT NULL DEFAULT true,  -- alertes système (no-show, expire, etc.)

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- L'UNIQUE sur user_id crée déjà un index → lookup O(1) par user_id couvert.

COMMENT ON TABLE staff_notification_preferences IS
    'Préférences de notifications par utilisateur. UNIQUE par user_id. Tenant-agnostic. Self-service /me (scope via JWT). Gap #5 — port legacy B.5.';
COMMENT ON COLUMN staff_notification_preferences.booking IS
    'Réservations activités : resource_booking + seminar + bookings.';
COMMENT ON COLUMN staff_notification_preferences.reservation IS
    'Réservations table restaurants : type reservation classique.';
COMMENT ON COLUMN staff_notification_preferences.feedback IS
    'Avis membres : feedback + réponses.';
COMMENT ON COLUMN staff_notification_preferences.loyalty IS
    'Points fidélité : loyalty + points + tier_upgrade + promotion.';
COMMENT ON COLUMN staff_notification_preferences.system IS
    'Alertes système : type system (no-show, expire, etc.).';
