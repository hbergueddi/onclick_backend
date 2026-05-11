-- ════════════════════════════════════════════════════════════════════
-- V2 — Compléter le core : notification + media + audit_log + configuration
-- ════════════════════════════════════════════════════════════════════
-- Architecture cible §7, §14, §15, §19 (Feature Flags)
-- 12 tables au total
-- ════════════════════════════════════════════════════════════════════

-- ─── core/notification §7 ────────────────────────────────────────────
-- Push, in-app, email — unifié avec canal explicite
CREATE TABLE notifications (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id   uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                text NOT NULL CHECK (type IN ('reservation', 'loyalty', 'promotion', 'community', 'support', 'system', 'announcement')),
    channel             text NOT NULL DEFAULT 'inapp' CHECK (channel IN ('inapp', 'push', 'email', 'sms')),
    title               text NOT NULL,
    body                text NOT NULL,
    link                text,
    metadata            jsonb NOT NULL DEFAULT '{}'::jsonb,
    read_at             timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_recipient ON notifications(recipient_user_id, created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type, created_at DESC);
COMMENT ON TABLE notifications IS 'Notifications utilisateur multi-canal (in-app/push/email/sms)';

-- Campagnes marketing — batch send programmé
CREATE TABLE notification_campaigns (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title           text NOT NULL,
    message         text NOT NULL,
    target_segment  text,
    scheduled_at    timestamptz,
    sent_at         timestamptz,
    status          text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'scheduled', 'sending', 'sent', 'cancelled', 'failed')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_notification_campaigns_tenant ON notification_campaigns(tenant_id, scheduled_at);
CREATE INDEX idx_notification_campaigns_pending ON notification_campaigns(scheduled_at) WHERE status IN ('scheduled', 'sending');
COMMENT ON TABLE notification_campaigns IS 'Campagnes marketing multi-utilisateurs avec scheduling';

-- Tokens FCM/APNs par device
CREATE TABLE device_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           text NOT NULL UNIQUE,
    platform        text CHECK (platform IN ('ios', 'android', 'web')),
    app_id          text,
    last_used_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
COMMENT ON TABLE device_tokens IS 'Tokens push FCM/APNs par device (Capacitor mobile)';

-- ─── core/media §14 ──────────────────────────────────────────────────
-- Polymorphique : entity_type + entity_id ; remplace restaurant_media + avatars + community_covers + ticket-photos
CREATE TABLE medias (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    url             text NOT NULL,
    media_type      text NOT NULL CHECK (media_type IN ('image', 'video', 'audio', 'pdf')),
    mime_type       text,
    size_bytes      bigint,
    sort_order      integer DEFAULT 0,
    metadata        jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_medias_entity ON medias(entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_medias_type ON medias(media_type) WHERE deleted_at IS NULL;
COMMENT ON TABLE medias IS 'Médias polymorphiques (images, vidéos, audio, pdf) attachés à n''importe quelle entité';

-- Fichiers (separate from medias pour pouvoir évoluer indépendamment — pdf attachés à tickets etc.)
CREATE TABLE file_attachments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    path            text NOT NULL,
    mime_type       text,
    size_bytes      bigint,
    original_name   text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_file_attachments_entity ON file_attachments(entity_type, entity_id) WHERE deleted_at IS NULL;
COMMENT ON TABLE file_attachments IS 'Fichiers (PDF, docs) attachés à n''importe quelle entité (polymorphique)';

-- ─── core/audit_log §15 ──────────────────────────────────────────────
-- Trace des actions admin/utilisateur (qui a fait quoi, quand)
CREATE TABLE audit_logs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid REFERENCES users(id) ON DELETE SET NULL,
    tenant_id       uuid REFERENCES tenants(id) ON DELETE SET NULL,
    entity_type     text NOT NULL,
    entity_id       uuid,
    action          text NOT NULL,
    diff            jsonb NOT NULL DEFAULT '{}'::jsonb,
    ip_address      text,
    user_agent      text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_tenant ON audit_logs(tenant_id, created_at DESC);
COMMENT ON TABLE audit_logs IS 'Audit trail centralisé — qui a fait quoi sur quelle entité';

-- Events système — domain events persistés
CREATE TABLE system_events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type            text NOT NULL,
    payload         jsonb NOT NULL DEFAULT '{}'::jsonb,
    processed_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_system_events_type ON system_events(type, created_at DESC);
CREATE INDEX idx_system_events_unprocessed ON system_events(created_at) WHERE processed_at IS NULL;
COMMENT ON TABLE system_events IS 'Domain events persistés pour replay/audit (alternative pré-Modulith JPA event store)';

-- Erreurs applicatives — log centralisé pour Sentry-equivalent
CREATE TABLE error_logs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name    text NOT NULL,
    message         text NOT NULL,
    stacktrace      text,
    severity        text NOT NULL DEFAULT 'error' CHECK (severity IN ('debug', 'info', 'warn', 'error', 'fatal')),
    metadata        jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_error_logs_service_severity ON error_logs(service_name, severity, created_at DESC);
COMMENT ON TABLE error_logs IS 'Erreurs applicatives — équivalent Sentry persisté en local';

-- Cron jobs / batch executions tracking
CREATE TABLE job_executions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name        text NOT NULL,
    status          text NOT NULL CHECK (status IN ('running', 'success', 'failed', 'cancelled')),
    started_at      timestamptz NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    result          jsonb,
    error_message   text
);
CREATE INDEX idx_job_executions_job_name ON job_executions(job_name, started_at DESC);
CREATE INDEX idx_job_executions_running ON job_executions(started_at) WHERE status = 'running';
COMMENT ON TABLE job_executions IS 'Statut des cron jobs / batch (succès, échec, durée)';

-- ─── core/configuration §19 ──────────────────────────────────────────
-- Feature flags dynamiques (différents de tenant_features qui sont statiques par tenant)
CREATE TABLE feature_flags (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    description     text,
    enabled         boolean NOT NULL DEFAULT false,
    rollout_pct     integer NOT NULL DEFAULT 0 CHECK (rollout_pct BETWEEN 0 AND 100),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_feature_flags_enabled ON feature_flags(enabled);
COMMENT ON TABLE feature_flags IS 'Feature flags dynamiques (vs tenant_features statiques par tenant)';

-- Targeting du feature flag (par user, par tenant, par role)
CREATE TABLE feature_flag_targets (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_id uuid NOT NULL REFERENCES feature_flags(id) ON DELETE CASCADE,
    target_type     text NOT NULL CHECK (target_type IN ('user', 'tenant', 'role')),
    target_id       uuid NOT NULL,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (feature_flag_id, target_type, target_id)
);
CREATE INDEX idx_feature_flag_targets_target ON feature_flag_targets(target_type, target_id);
COMMENT ON TABLE feature_flag_targets IS 'Targeting custom des feature flags (overrides par user/tenant/role)';

-- Cache TTL config — pour Redis ou cache JVM
CREATE TABLE cache_configurations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_name      text NOT NULL UNIQUE,
    ttl_seconds     integer NOT NULL,
    max_entries     integer,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE cache_configurations IS 'Config TTL et taille pour les caches (gestionnables sans redeploy)';
