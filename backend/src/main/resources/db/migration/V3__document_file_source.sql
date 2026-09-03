-- Apply/commit this file BEFORE V4. PostgreSQL requires a newly added enum value
-- to be committed before another statement can use it.
ALTER TYPE app.document_source_type ADD VALUE IF NOT EXISTS 'FILE';
