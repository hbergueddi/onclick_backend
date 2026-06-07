-- V81 — Commentaires + mentions du mur communautaire membre (axe A.2).
--
-- Complète A.1 (V80 likes) : commentaires sur les posts approuvés + mentions
-- (@membre) sur les posts ET les commentaires. mentioned_user_ids = text[] de
-- UUID (cohérent avec le pattern tags/allergens du schéma Spring, pas uuid[]).

CREATE TABLE IF NOT EXISTS member_post_comments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             uuid NOT NULL REFERENCES member_posts(id) ON DELETE CASCADE,
    author_id           uuid NOT NULL,
    content             varchar(500) NOT NULL,
    mentioned_user_ids  text[],
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_member_post_comments_post ON member_post_comments(post_id, created_at);

-- Mentions sur le post lui-même (le commentaire en a aussi, colonne ci-dessus).
ALTER TABLE member_posts ADD COLUMN IF NOT EXISTS mentioned_user_ids text[];

COMMENT ON TABLE member_post_comments IS 'A.2 — commentaires du mur communautaire (+ mentions).';
COMMENT ON COLUMN member_posts.mentioned_user_ids IS 'A.2 — UUID (text[]) des membres mentionnés dans le post.';
