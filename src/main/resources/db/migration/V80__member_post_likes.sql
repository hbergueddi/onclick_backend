-- V80 — Likes du mur communautaire membre (axe A — flux membre, tranche A.1).
--
-- Le module membercircle (V76 member_posts) ne portait que la modération admin.
-- A.1 ajoute le flux MEMBRE : création (status=pending), feed des posts approuvés,
-- et likes. Table member_post_likes : 1 like par (post, user), idempotent.

CREATE TABLE IF NOT EXISTS member_post_likes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     uuid NOT NULL REFERENCES member_posts(id) ON DELETE CASCADE,
    user_id     uuid NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),  -- TimestampedEntity (validate)
    CONSTRAINT uq_member_post_like UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_member_post_likes_post ON member_post_likes(post_id);
CREATE INDEX IF NOT EXISTS idx_member_post_likes_user ON member_post_likes(user_id);

COMMENT ON TABLE member_post_likes IS
    'A — likes du mur communautaire membre (1 par post×user, idempotent).';
