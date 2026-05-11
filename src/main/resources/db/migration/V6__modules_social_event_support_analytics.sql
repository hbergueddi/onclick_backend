-- ════════════════════════════════════════════════════════════════════
-- V6 — community + social + promotion + event + support + analytics + tables avancées
-- ════════════════════════════════════════════════════════════════════
-- §7, §8, §9, §13, §19 — 17 tables au total
-- ════════════════════════════════════════════════════════════════════

-- ─── modules/promotion §7 ────────────────────────────────────────────
CREATE TABLE offers (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    title           text NOT NULL,
    description     text,
    starts_at       timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    discount_pct    numeric(5, 2) CHECK (discount_pct >= 0 AND discount_pct <= 100),
    discount_amount numeric(12, 2),
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    CHECK (expires_at > starts_at)
);
CREATE INDEX idx_offers_restaurant_active ON offers(restaurant_id, expires_at) WHERE deleted_at IS NULL AND enabled = true;
COMMENT ON TABLE offers IS 'Offres / promotions par restaurant (§7)';

-- ─── modules/community §8 ────────────────────────────────────────────
CREATE TABLE posts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         text NOT NULL,
    visibility      text NOT NULL DEFAULT 'public' CHECK (visibility IN ('public', 'friends', 'private')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);
CREATE INDEX idx_posts_author_date ON posts(author_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_public_date ON posts(created_at DESC) WHERE deleted_at IS NULL AND visibility = 'public';
COMMENT ON TABLE posts IS 'Posts communauté (feed social)';

CREATE TABLE comments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);
CREATE INDEX idx_comments_post_date ON comments(post_id, created_at) WHERE deleted_at IS NULL;
COMMENT ON TABLE comments IS 'Commentaires sur posts communauté';

CREATE TABLE post_likes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_likes_post ON post_likes(post_id);
COMMENT ON TABLE post_likes IS 'Likes sur posts (1 user × 1 post = 1 ligne)';

-- ─── modules/social §8 ───────────────────────────────────────────────
CREATE TABLE friendships (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user2_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'declined', 'blocked')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    accepted_at     timestamptz,
    CHECK (user1_id < user2_id),  -- canonique : toujours user1_id < user2_id (évite les doublons)
    UNIQUE (user1_id, user2_id)
);
CREATE INDEX idx_friendships_user1 ON friendships(user1_id) WHERE status = 'accepted';
CREATE INDEX idx_friendships_user2 ON friendships(user2_id) WHERE status = 'accepted';
COMMENT ON TABLE friendships IS 'Amitiés bidirectionnelles (un seul row pour le couple grâce à CHECK user1 < user2)';

CREATE TABLE referrals (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id    uuid REFERENCES users(id) ON DELETE CASCADE,
    referral_code       text NOT NULL,
    status              text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'activated', 'expired')),
    activated_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_referrals_referrer ON referrals(referrer_id, created_at DESC);
CREATE INDEX idx_referrals_referred ON referrals(referred_user_id) WHERE referred_user_id IS NOT NULL;
CREATE INDEX idx_referrals_code ON referrals(referral_code);
COMMENT ON TABLE referrals IS 'Parrainages avec code unique et status d''activation';

-- ─── modules/event §9 ────────────────────────────────────────────────
CREATE TABLE events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    restaurant_id   uuid REFERENCES restaurants(id) ON DELETE SET NULL,
    title           text NOT NULL,
    description     text,
    event_type      text,
    event_at        timestamptz NOT NULL,
    capacity        integer CHECK (capacity > 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_events_tenant_date ON events(tenant_id, event_at) WHERE deleted_at IS NULL;
COMMENT ON TABLE events IS 'Événements (soirée, dégustation, séminaire) — global tenant ou par restaurant';

CREATE TABLE event_participations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          text NOT NULL DEFAULT 'going' CHECK (status IN ('going', 'maybe', 'declined', 'attended')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);
CREATE INDEX idx_event_participations_event ON event_participations(event_id);
CREATE INDEX idx_event_participations_user ON event_participations(user_id);
COMMENT ON TABLE event_participations IS 'RSVPs sur événements (going/maybe/declined/attended)';

-- ─── modules/support §13 ─────────────────────────────────────────────
CREATE TABLE support_tickets (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    opened_by       uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category        text NOT NULL,
    priority        text NOT NULL DEFAULT 'normal' CHECK (priority IN ('low', 'normal', 'high', 'urgent')),
    status          text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'in_progress', 'resolved', 'closed')),
    subject         text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    resolved_at     timestamptz,
    closed_at       timestamptz,
    assigned_to     uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_support_tickets_status ON support_tickets(status, priority, created_at DESC);
CREATE INDEX idx_support_tickets_opened_by ON support_tickets(opened_by, created_at DESC);
CREATE INDEX idx_support_tickets_assigned ON support_tickets(assigned_to, status) WHERE assigned_to IS NOT NULL;
COMMENT ON TABLE support_tickets IS 'Tickets support client';

CREATE TABLE ticket_messages (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       uuid NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message         text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_messages_ticket ON ticket_messages(ticket_id, created_at);
COMMENT ON TABLE ticket_messages IS 'Messages d''un ticket support (thread chronologique)';

CREATE TABLE ticket_attachments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       uuid NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    url             text NOT NULL,
    file_name       text,
    mime_type       text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments(ticket_id);
COMMENT ON TABLE ticket_attachments IS 'Pièces jointes (screenshots, PDFs) d''un ticket support';

-- ─── modules/analytics — Search + API Mgmt §19 ───────────────────────
CREATE TABLE restaurant_search_documents (
    restaurant_id   uuid PRIMARY KEY REFERENCES restaurants(id) ON DELETE CASCADE,
    document        tsvector,
    indexed_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_restaurant_search_documents_doc ON restaurant_search_documents USING gin(document);
COMMENT ON TABLE restaurant_search_documents IS 'Index de recherche full-text (Postgres tsvector) — sync vers Elasticsearch';

-- ─── API management §19 ──────────────────────────────────────────────
CREATE TABLE api_clients (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid REFERENCES tenants(id) ON DELETE CASCADE,
    name            text NOT NULL,
    description     text,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_clients_tenant ON api_clients(tenant_id) WHERE enabled = true;
COMMENT ON TABLE api_clients IS 'Clients API (intégrations partenaires)';

CREATE TABLE api_keys (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    api_client_id   uuid NOT NULL REFERENCES api_clients(id) ON DELETE CASCADE,
    key_hash        text NOT NULL UNIQUE,
    key_prefix      text NOT NULL,
    scopes          jsonb NOT NULL DEFAULT '[]'::jsonb,
    enabled         boolean NOT NULL DEFAULT true,
    last_used_at    timestamptz,
    expires_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    revoked_at      timestamptz
);
CREATE INDEX idx_api_keys_client ON api_keys(api_client_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix) WHERE revoked_at IS NULL;
COMMENT ON TABLE api_keys IS 'Clés API hashées (key_hash) + prefix pour identification rapide';

CREATE TABLE webhooks (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    api_client_id   uuid NOT NULL REFERENCES api_clients(id) ON DELETE CASCADE,
    url             text NOT NULL,
    secret          text,
    event_types     jsonb NOT NULL DEFAULT '[]'::jsonb,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhooks_client ON webhooks(api_client_id) WHERE enabled = true;
COMMENT ON TABLE webhooks IS 'Webhooks sortants — URLs à appeler quand un event se produit';

CREATE TABLE webhook_deliveries (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      uuid NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type      text NOT NULL,
    payload         jsonb NOT NULL,
    status_code     integer,
    response_body   text,
    attempts        integer NOT NULL DEFAULT 0,
    succeeded_at    timestamptz,
    failed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_deliveries_webhook_date ON webhook_deliveries(webhook_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_failed ON webhook_deliveries(created_at) WHERE failed_at IS NOT NULL AND succeeded_at IS NULL;
COMMENT ON TABLE webhook_deliveries IS 'Historique des appels webhook (retries, status, response)';
