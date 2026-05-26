-- ════════════════════════════════════════════════════════════════════
-- V39 — Répare le flux « demande d'ami → accepter » (Pocket /circle/friends)
-- ════════════════════════════════════════════════════════════════════
-- Diagnostic (audit contrat front↔back) : le loop demande→accept était cassé
-- de bout en bout, pour 2 raisons schéma :
--
--  1) friendships n'a PAS de direction (CHECK user1_id < user2_id, sans
--     requester). Impossible de lister les demandes REÇUES (vs envoyées) →
--     la section « Demandes reçues » de CircleFriends ne pouvait pas exister.
--     → on ajoute requested_by = l'auteur de la demande.
--
--  2) La notif serveur de demande d'ami devait porter type='friend_request'
--     pour que la cloche (GlassHeader) ouvre la pop-up d'acceptation, mais le
--     CHECK de notifications.type (V2) n'autorisait pas cette valeur.
--     → on étend le CHECK avec 'friend_request'.
--
-- Idempotent (IF EXISTS / IF NOT EXISTS / DROP+ADD du CHECK).
-- ════════════════════════════════════════════════════════════════════

-- 1) Direction de la demande d'amitié (auteur). NULL pour les rows historiques
--    (toutes 'accepted' en pratique → la direction n'a d'intérêt que pour 'pending').
ALTER TABLE friendships
    ADD COLUMN IF NOT EXISTS requested_by uuid REFERENCES users(id) ON DELETE SET NULL;

-- Index partiel : on ne requête la direction que pour les demandes en attente.
CREATE INDEX IF NOT EXISTS idx_friendships_requested_by
    ON friendships(requested_by) WHERE status = 'pending';

COMMENT ON COLUMN friendships.requested_by IS
    'Auteur de la demande d''amitié (pour distinguer reçue vs envoyée). NULL = historique.';

-- 2) Autorise type='friend_request' sur les notifications (cloche → pop-up accept).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('reservation', 'loyalty', 'promotion', 'community',
                    'support', 'system', 'announcement', 'friend_request'));
