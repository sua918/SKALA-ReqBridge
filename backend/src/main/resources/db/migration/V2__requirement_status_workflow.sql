SET search_path TO app, public;

-- API 0.3.0 replaces the broad OPEN state with explicit workflow states.
-- Preserve existing rows by deriving the most specific state available from history.
ALTER TABLE requirement ADD COLUMN status_v030 TEXT;

UPDATE requirement r
SET status_v030 = CASE
    WHEN r.status::TEXT <> 'OPEN' THEN r.status::TEXT
    WHEN EXISTS (
        SELECT 1
        FROM clarification c
        WHERE c.requirement_id = r.id
    ) THEN 'CLARIFYING'
    WHEN EXISTS (
        SELECT 1
        FROM ambiguity_issue i
        WHERE i.requirement_id = r.id
    ) THEN 'AMBIGUOUS'
    ELSE 'EXTRACTED'
END;

ALTER TABLE requirement ALTER COLUMN status DROP DEFAULT;
ALTER TABLE requirement DROP CONSTRAINT ck_requirement_confirmation;

ALTER TYPE requirement_status RENAME TO requirement_status_v020;
CREATE TYPE requirement_status AS ENUM (
    'EXTRACTED',
    'AMBIGUOUS',
    'CLARIFYING',
    'IN_REVIEW',
    'CONFIRMED'
);

ALTER TABLE requirement
    ALTER COLUMN status TYPE requirement_status
    USING status_v030::requirement_status;

ALTER TABLE requirement DROP COLUMN status_v030;
ALTER TABLE requirement ALTER COLUMN status SET DEFAULT 'EXTRACTED';

ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_confirmation CHECK (
        (status = 'CONFIRMED' AND approved_revision_id IS NOT NULL
            AND confirmed_text IS NOT NULL AND confirmed_at IS NOT NULL)
        OR (status <> 'CONFIRMED' AND approved_revision_id IS NULL
            AND confirmed_text IS NULL AND confirmed_at IS NULL)
    );

DROP TYPE requirement_status_v020;
