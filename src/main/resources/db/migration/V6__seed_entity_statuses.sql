-- ============================================================================
-- V6 — Seed canonique des codes de statuts métier
-- ============================================================================
--
-- Phase 2 de la refacto status. Insère les codes canoniques (~130 lignes)
-- pour les 32 entity_type qui ont aujourd'hui un champ `status` String à
-- migrer.
--
-- Conventions :
--   * code      : français snake_case sans accent (canonique, immutable)
--   * label_fr  : libellé humain FR avec accents et casse propre
--   * label_en  : libellé EN pour i18n (peut être NULL si pas de mapping)
--   * sort_order: ordre logique du workflow (1 = premier état, terminaux à la fin)
--   * is_terminal: true pour les statuts finaux (workflow stoppé)
--
-- Idempotent : ON CONFLICT (entity_type, code) DO NOTHING — relancer la
-- migration en local ne casse rien (ne ré-insère pas).
--
-- Référence des valeurs : audit V5 + CHECK constraints DB + ONECLICK-BRIEF.md
-- L.176 (workflow facture) + ARCHITECTURE.md (workflow contrat / résa).
-- ============================================================================

INSERT INTO public.entity_statuses (entity_type, code, label_fr, label_en, sort_order, is_terminal) VALUES
-- ── elite_application : workflow candidature Elite Club ────────────────────
('elite_application', 'en_attente', 'En attente',  'Pending',   1, false),
('elite_application', 'approuvee',  'Approuvée',   'Approved',  2, true),
('elite_application', 'refusee',    'Refusée',     'Refused',   3, true),

-- ── elite_rsvp : RSVP événement Elite (mappé depuis CHECK confirmed/cancelled/waitlist) ──
('elite_rsvp', 'confirmee',     'Confirmée',     'Confirmed', 1, false),
('elite_rsvp', 'liste_attente', 'Liste d''attente','Waitlist',  2, false),
('elite_rsvp', 'annulee',       'Annulée',       'Cancelled', 3, true),

-- ── event_rsvp : RSVP TenantEvent (mappé depuis CHECK attending/maybe/not_attending) ──
('event_rsvp', 'confirmee', 'J''y vais',  'Attending',     1, false),
('event_rsvp', 'peut_etre', 'Peut-être',  'Maybe',         2, false),
('event_rsvp', 'pas_dispo', 'Pas dispo',  'Not attending', 3, true),

-- ── fraud_alert : workflow détection fraude (Snap2Earn anomalies) ──────────
('fraud_alert', 'en_attente', 'En attente', 'Pending',     1, false),
('fraud_alert', 'en_cours',   'En cours',   'In progress', 2, false),
('fraud_alert', 'resolue',    'Résolue',    'Resolved',    3, true),
('fraud_alert', 'rejetee',    'Rejetée',    'Rejected',    4, true),

-- ── friendship : demandes d'amitié (mappé depuis CHECK pending/accepted/refused) ──
('friendship', 'en_attente', 'En attente', 'Pending',  1, false),
('friendship', 'acceptee',   'Acceptée',   'Accepted', 2, true),
('friendship', 'refusee',    'Refusée',    'Refused',  3, true),

-- ── gain_rule_request : workflow approbation règle gain ────────────────────
('gain_rule_request', 'en_attente', 'En attente', 'Pending',  1, false),
('gain_rule_request', 'approuvee',  'Approuvée',  'Approved', 2, true),
('gain_rule_request', 'refusee',    'Refusée',    'Refused',  3, true),

-- ── monitor_log : telemetry status (succès / erreur de l'event) ────────────
('monitor_log', 'ok',      'OK',         'OK',      1, false),
('monitor_log', 'pending', 'En attente', 'Pending', 2, false),
('monitor_log', 'warning', 'Warning',    'Warning', 3, false),
('monitor_log', 'error',   'Erreur',     'Error',   4, true),

-- ── no_show_dispute : contestation no-show 48h (3 phases) ──────────────────
('no_show_dispute', 'en_attente', 'En attente', 'Pending',  1, false),
('no_show_dispute', 'acceptee',   'Acceptée',   'Accepted', 2, true),
('no_show_dispute', 'refusee',    'Refusée',    'Refused',  3, true),

-- ── onboarding_request : workflow onboarding restos (review admin) ─────────
('onboarding_request', 'en_attente', 'En attente', 'Pending',  1, false),
('onboarding_request', 'approuvee',  'Approuvée',  'Approved', 2, true),
('onboarding_request', 'refusee',    'Refusée',    'Refused',  3, true),

-- ── oneclick_hi_invoice : workflow facture (cf ONECLICK-BRIEF.md L.176) ────
('oneclick_hi_invoice', 'brouillon',     'Brouillon',     'Draft',     1, false),
('oneclick_hi_invoice', 'en_attente',    'En attente',    'Pending',   2, false),
('oneclick_hi_invoice', 'validee',       'Validée',       'Validated', 3, false),
('oneclick_hi_invoice', 'envoyee',       'Envoyée',       'Sent',      4, false),
('oneclick_hi_invoice', 'en_retard',     'En retard',     'Overdue',   5, false),
('oneclick_hi_invoice', 'rappel_envoye', 'Rappel envoyé', 'Reminded',  6, false),
('oneclick_hi_invoice', 'payee',         'Payée',         'Paid',      7, true),
('oneclick_hi_invoice', 'annulee',       'Annulée',       'Cancelled', 8, true),

-- ── partner_contract : workflow contrat partenaire ─────────────────────────
('partner_contract', 'prospect',       'Prospect',         'Prospect',     1, false),
('partner_contract', 'en_negociation', 'En négociation',   'Negotiation',  2, false),
('partner_contract', 'envoye',         'Envoyé',           'Sent',         3, false),
('partner_contract', 'actif',          'Actif',            'Active',       4, false),
('partner_contract', 'renouvellement', 'En renouvellement','Renewal',      5, false),
('partner_contract', 'expire',         'Expiré',           'Expired',      6, true),
('partner_contract', 'resilie',        'Résilié',          'Terminated',   7, true),

-- ── point_distribution : workflow distribution points par admin ────────────
('point_distribution', 'planifie',   'Planifiée',  'Scheduled',   1, false),
('point_distribution', 'en_cours',   'En cours',   'In progress', 2, false),
('point_distribution', 'distribuee', 'Distribuée', 'Distributed', 3, true),
('point_distribution', 'annulee',    'Annulée',    'Cancelled',   4, true),

-- ── promo_notification_request : approbation push promo par admin ──────────
('promo_notification_request', 'en_attente', 'En attente', 'Pending',  1, false),
('promo_notification_request', 'approuvee',  'Approuvée',  'Approved', 2, false),
('promo_notification_request', 'envoyee',    'Envoyée',    'Sent',     3, true),
('promo_notification_request', 'rejetee',    'Rejetée',    'Rejected', 4, true),

-- ── redemption_otp_request : workflow OTP redemption (CHECK pending/consumed/expired/cancelled) ──
('redemption_otp_request', 'en_attente', 'En attente', 'Pending',   1, false),
('redemption_otp_request', 'consommee',  'Consommée',  'Consumed',  2, true),
('redemption_otp_request', 'expiree',    'Expirée',    'Expired',   3, true),
('redemption_otp_request', 'annulee',    'Annulée',    'Cancelled', 4, true),

-- ── referral : programme parrainage (CHECK en_attente/actif/expiré) ────────
('referral', 'en_attente', 'En attente', 'Pending',  1, false),
('referral', 'actif',      'Actif',      'Active',   2, false),
('referral', 'expire',     'Expiré',     'Expired',  3, true),

-- ── reservation : workflow résa (mappé depuis enum PG reservation_status) ──
('reservation', 'demandee',           'Demandée',          'Requested',          1, false),
('reservation', 'contre_proposition', 'Contre-proposition','Counter-proposal',   2, false),
('reservation', 'confirmee',          'Confirmée',         'Confirmed',          3, false),
('reservation', 'placee',             'Placée',            'Seated',             4, false),
('reservation', 'honoree',            'Honorée',           'Honored',            5, true),
('reservation', 'terminee',           'Terminée',          'Completed',          6, true),
('reservation', 'no_show',            'No-show',           'No-show',            7, true),
('reservation', 'refusee',            'Refusée',           'Refused',            8, true),
('reservation', 'annulee',            'Annulée',           'Cancelled',          9, true),

-- ── reservation_guest : statut invité dans une résa ────────────────────────
('reservation_guest', 'en_attente', 'En attente', 'Pending',   1, false),
('reservation_guest', 'lie',        'Lié',        'Linked',    2, false),
('reservation_guest', 'accepte',    'Accepté',    'Accepted',  3, false),
('reservation_guest', 'refuse',     'Refusé',     'Refused',   4, true),
('reservation_guest', 'annule',     'Annulé',     'Cancelled', 5, true),

-- ── resource_booking : workflow PCC (mappé depuis enum PG resource_booking_status) ──
('resource_booking', 'demandee',  'Demandée',  'Requested', 1, false),
('resource_booking', 'confirmee', 'Confirmée', 'Confirmed', 2, false),
('resource_booking', 'honoree',   'Honorée',   'Honored',   3, true),
('resource_booking', 'no_show',   'No-show',   'No-show',   4, true),
('resource_booking', 'annulee',   'Annulée',   'Cancelled', 5, true),

-- ── restaurant : statut restaurant (CHECK actif/inactif/suspendu/prospect) ─
('restaurant', 'prospect',  'Prospect',  'Prospect',  1, false),
('restaurant', 'actif',     'Actif',     'Active',    2, false),
('restaurant', 'inactif',   'Inactif',   'Inactive',  3, false),
('restaurant', 'suspendu',  'Suspendu',  'Suspended', 4, false),
('restaurant', 'archive',   'Archivé',   'Archived',  5, true),

-- ── restaurant_media : statut photo/vidéo restaurant ───────────────────────
('restaurant_media', 'actif',   'Actif',   'Active',   1, false),
('restaurant_media', 'archive', 'Archivé', 'Archived', 2, true),

-- ── restaurant_restitution : workflow restitution points expirés ───────────
('restaurant_restitution', 'en_attente', 'En attente', 'Pending',   1, false),
('restaurant_restitution', 'traitee',    'Traitée',    'Processed', 2, true),
('restaurant_restitution', 'annulee',    'Annulée',    'Cancelled', 3, true),

-- ── restaurant_service : statut service (déjeuner, dîner, brunch…) ─────────
('restaurant_service', 'actif',   'Actif',   'Active',   1, false),
('restaurant_service', 'inactif', 'Inactif', 'Inactive', 2, false),
('restaurant_service', 'archive', 'Archivé', 'Archived', 3, true),

-- ── restaurant_staff : statut staff resto ──────────────────────────────────
('restaurant_staff', 'actif',     'Actif',     'Active',   1, false),
('restaurant_staff', 'desactive', 'Désactivé', 'Disabled', 2, true),

-- ── restaurant_table : statut table physique ───────────────────────────────
('restaurant_table', 'actif',   'Actif',   'Active',   1, false),
('restaurant_table', 'inactif', 'Inactif', 'Inactive', 2, false),
('restaurant_table', 'archive', 'Archivé', 'Archived', 3, true),

-- ── restaurant_zone : statut zone de service ───────────────────────────────
('restaurant_zone', 'actif',   'Actif',   'Active',   1, false),
('restaurant_zone', 'inactif', 'Inactif', 'Inactive', 2, false),
('restaurant_zone', 'archive', 'Archivé', 'Archived', 3, true),

-- ── scanned_ticket : workflow validation Snap2Earn ─────────────────────────
('scanned_ticket', 'en_attente', 'En attente', 'Pending',  1, false),
('scanned_ticket', 'valide',     'Validé',     'Validated', 2, true),
('scanned_ticket', 'rejete',     'Rejeté',     'Rejected',  3, true),

-- ── seminar_request : workflow demande séminaire B2B ───────────────────────
('seminar_request', 'demandee',     'Demandée',     'Requested',   1, false),
('seminar_request', 'en_traitement','En traitement','In progress', 2, false),
('seminar_request', 'confirmee',    'Confirmée',    'Confirmed',   3, true),
('seminar_request', 'refusee',      'Refusée',      'Refused',     4, true),
('seminar_request', 'annulee',      'Annulée',      'Cancelled',   5, true),

-- ── support_ticket : workflow ticket support (CHECK ouvert/en_attente/en_cours/résolu) ──
('support_ticket', 'ouvert',     'Ouvert',     'Open',        1, false),
('support_ticket', 'en_attente', 'En attente', 'Pending',     2, false),
('support_ticket', 'en_cours',   'En cours',   'In progress', 3, false),
('support_ticket', 'resolu',     'Résolu',     'Resolved',    4, true),
('support_ticket', 'ferme',      'Fermé',      'Closed',      5, true),

-- ── system_health_check : telemetry status système ─────────────────────────
('system_health_check', 'ok',       'OK',       'OK',       1, false),
('system_health_check', 'warning',  'Warning',  'Warning',  2, false),
('system_health_check', 'critical', 'Critique', 'Critical', 3, false),

-- ── team_invitation : workflow invitation staff resto ──────────────────────
('team_invitation', 'en_attente', 'En attente', 'Pending',  1, false),
('team_invitation', 'acceptee',   'Acceptée',   'Accepted', 2, true),
('team_invitation', 'refusee',    'Refusée',    'Refused',  3, true),
('team_invitation', 'expiree',    'Expirée',    'Expired',  4, true),
('team_invitation', 'annulee',    'Annulée',    'Cancelled',5, true),

-- ── tenant : statut tenant whitelabel (CHECK actif/suspendu/archivé) ───────
('tenant', 'actif',    'Actif',    'Active',    1, false),
('tenant', 'suspendu', 'Suspendu', 'Suspended', 2, false),
('tenant', 'archive',  'Archivé',  'Archived',  3, true),

-- ── tenant_event : workflow événement tenant ──────────────────────────────
('tenant_event', 'brouillon', 'Brouillon', 'Draft',     1, false),
('tenant_event', 'publie',    'Publié',    'Published', 2, false),
('tenant_event', 'actif',     'Actif',     'Active',    3, false),
('tenant_event', 'annule',    'Annulé',    'Cancelled', 4, true),
('tenant_event', 'termine',   'Terminé',   'Completed', 5, true)

ON CONFLICT (entity_type, code) DO NOTHING;
