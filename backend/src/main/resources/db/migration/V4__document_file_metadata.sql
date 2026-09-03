-- Requires V3 to have committed. Existing TEXT rows remain unchanged.
ALTER TABLE app.document
    ADD COLUMN storage_path TEXT,
    ADD COLUMN original_filename TEXT,
    ADD COLUMN mime_type VARCHAR(100),
    ADD COLUMN file_size_bytes BIGINT;

ALTER TABLE app.document ADD CONSTRAINT ck_document_file_metadata CHECK (
    (source_type = 'TEXT' AND storage_path IS NULL AND original_filename IS NULL
        AND mime_type IS NULL AND file_size_bytes IS NULL)
    OR
    (source_type = 'FILE' AND storage_path IS NOT NULL AND btrim(storage_path) <> ''
        AND original_filename IS NOT NULL AND btrim(original_filename) <> ''
        AND mime_type IS NOT NULL AND mime_type = 'application/pdf'
        AND file_size_bytes IS NOT NULL AND file_size_bytes BETWEEN 1 AND 10485760)
);

COMMENT ON COLUMN app.document.storage_path IS 'Private Storage object key; not a public URL';
COMMENT ON COLUMN app.document.original_filename IS 'Original client filename; never used as object key';
COMMENT ON COLUMN app.document.content IS 'TEXT: original text. FILE: validated text extracted from PDF';
