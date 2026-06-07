-- ════════════════════════════════════════════════════════════════════
-- V88 — Activation de compte membre par email (Gap #10, AuthFlowGate)
-- ════════════════════════════════════════════════════════════════════
-- Quand un staff "inscrit un membre" (Snap2Earn / EnrollMember) pour un NOUVEAU
-- client, le compte est créé côté serveur avec un mot de passe ALÉATOIRE que le
-- membre ne connaît pas. Le membre doit donc pouvoir définir son mot de passe à
-- la première connexion via un lien magique reçu par email.
--
-- Port du flux legacy Supabase (inviteUserByEmail → #type=invite → AuthFlowGate
-- → CreatePasswordDialog → auth.updateUser({password})) qui n'a pas d'équivalent
-- natif Spring. On le matérialise comme l'invitation tenant-admin (V78) : une
-- table d'invitations + token hashé (SHA-256) à usage unique.
--
-- Sécurité : on ne stocke JAMAIS le token en clair (seulement son SHA-256). Le
-- token clair ne vit que dans le lien email. Expiration 7 jours, single-use.
--
-- RBAC : pas de nouvelle ressource. La CRÉATION est interne (déclenchée par
-- l'event MemberEnrollmentRequestedEvent, jamais par un endpoint). L'ACCEPTATION
-- est PUBLIQUE (le membre n'a pas encore de session) — gardée par le token, pas
-- par une authority. /api/auth/** est déjà whitelisté dans SecurityConfig.
--
-- Additive pure (nouvelle table) → 0 impact existant.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS account_activation_invites (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email       varchar(256)  NOT NULL,
    token_hash  varchar(128)  NOT NULL,
    status      varchar(16)   NOT NULL DEFAULT 'pending',
    created_by  uuid          REFERENCES users(id) ON DELETE SET NULL,
    expires_at  timestamptz   NOT NULL,
    accepted_at timestamptz,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT account_activation_invites_status_chk CHECK (status IN ('pending', 'accepted', 'revoked', 'expired')),
    CONSTRAINT account_activation_invites_token_uq   UNIQUE (token_hash)
);

-- Lookup d'une invitation par utilisateur (anti-doublon : on ne recrée pas une
-- invitation pending si une existe déjà pour le user).
CREATE INDEX IF NOT EXISTS ix_account_activation_invites_user
    ON account_activation_invites (user_id);
