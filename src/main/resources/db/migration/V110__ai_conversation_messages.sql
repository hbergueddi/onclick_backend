-- V110 — Mémoire de conversation du chatbot (module core/ai).
-- Stocke les tours (user / assistant) par conversation pour un contexte multi-tours.
-- Append-only ; purge/rétention à définir ultérieurement (TTL ou job).

CREATE TABLE ai_conversation_messages (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  text        NOT NULL,
    user_id          uuid,
    role             text        NOT NULL,          -- 'user' | 'assistant'
    content          text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now()
);

-- Lecture des N derniers messages d'une conversation, par ordre chronologique.
CREATE INDEX idx_ai_conv_msg_conversation
    ON ai_conversation_messages (conversation_id, created_at);
