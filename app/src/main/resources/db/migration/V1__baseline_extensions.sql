-- Baseline: extensions only. Vector indexes (HNSW) come with the RAG work (M2).
CREATE EXTENSION IF NOT EXISTS vector;    -- pgvector: embeddings for filings (M2)
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram similarity: headline deduplication (M1)
