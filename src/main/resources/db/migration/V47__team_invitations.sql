-- ════════════════════════════════════════════════════════════════════
-- V47 — Domaine « team_invitations » : invitations d'équipe (pending staff)
-- ════════════════════════════════════════════════════════════════════
-- La page ProDesk GestionEquipe lisait/écrivait team_invitations via le shim
-- supabase.from("team_invitations") + le hook useTeamInvitations renvoyait []
-- (stub V1). Workflow : un gérant invite (pending) → accepted | disabled.
--
-- RBAC v2 senior strict : PAS de nouvelle ressource. Les invitations relèvent de
-- la gestion d'équipe → réutilisent l'autorité STAFF (V32 : CREATE/UPDATE/VIEW:STAFF
-- détenues par RESTAURATEUR/GROUP_ADMIN) + ABAC RestaurantAccessGuard
-- (requireAdminOrActiveStaffOf) côté contrôleur. @PreAuthorize = hasAuthority('VERB:STAFF'),
-- jamais isAuthenticated()/hasRole(). Aucun grant supplémentaire ⇒ pas de flush Redis.
--
-- Statut stocké en EN (pending/accepted/disabled) ; le hook front mappe en FR
-- (en_attente/acceptée/désactivée), conforme à la convention EN + traduction.
-- Immuable au niveau timestamp → CreatedAtEntity (created_at seul). Idempotent.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS team_invitations (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    invited_by    UUID        REFERENCES users(id) ON DELETE SET NULL,
    first_name    TEXT        NOT NULL CHECK (length(trim(first_name)) > 0),
    last_name     TEXT,
    phone         TEXT,
    role          TEXT        NOT NULL DEFAULT 'serveur',
    status        TEXT        NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'accepted', 'disabled')),
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_team_invitations_restaurant ON team_invitations(restaurant_id);

COMMENT ON TABLE team_invitations IS
    'Invitations d''équipe (pending staff) — gérées sous l''autorité STAFF + ABAC par restaurant.';
