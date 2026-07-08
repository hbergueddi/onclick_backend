-- V109 — Fondations RAG : extension pgvector + table documents/embeddings + index HNSW.
--
-- Contexte : module core/ai. Cette migration pose UNIQUEMENT le stockage vectoriel
-- (pas de pipeline d'ingestion ni de RAG à ce stade).
--
-- Dimension : 768 = nomic-embed-text (modèle d'embedding Ollama par défaut). Changer de
-- modèle d'embedding avec une autre dimensionnalité impose une nouvelle migration (ALTER de
-- la colonne) — le domaine (ports EmbeddingService/VectorStore) reste, lui, inchangé.
--
-- Pré-requis image : l'extension pgvector doit être disponible dans le cluster Postgres
-- (vérifié : vector 0.8.2). En cas de rebuild, utiliser une image type pgvector/pgvector:pg17.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_documents (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    content     text        NOT NULL,
    metadata    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(768),                          -- nomic-embed-text = 768 dims
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- Index HNSW pour la recherche par similarité cosinus (pgvector >= 0.5).
--   vector_cosine_ops → opérateur de distance cosinus (<=>).
--   m / ef_construction : valeurs par défaut raisonnables (compromis rappel / coût de build).
-- La colonne embedding est nullable : les lignes sans vecteur sont simplement ignorées par l'index.
CREATE INDEX idx_rag_documents_embedding_hnsw
    ON rag_documents
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
