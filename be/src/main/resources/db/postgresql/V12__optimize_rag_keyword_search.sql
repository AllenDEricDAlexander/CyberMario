CREATE INDEX IF NOT EXISTS idx_rag_chunk_content_trgm
    ON rag_document_chunk USING GIN (content gin_trgm_ops);
