-- 기존 3단계 requirement_status를 공용 5단계 계약으로 전환한다.
-- 대상: 기존 V1 SQL을 이미 적용한 app 스키마
-- 실행 전 애플리케이션 쓰기를 중지하고 DB 백업을 준비한다.

BEGIN;

LOCK TABLE app.requirement IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    current_values TEXT[];
BEGIN
    SELECT array_agg(enum_value.enumlabel ORDER BY enum_value.enumsortorder)
      INTO current_values
      FROM pg_type enum_type
      JOIN pg_namespace enum_namespace
        ON enum_namespace.oid = enum_type.typnamespace
      JOIN pg_enum enum_value
        ON enum_value.enumtypid = enum_type.oid
     WHERE enum_namespace.nspname = 'app'
       AND enum_type.typname = 'requirement_status';

    IF current_values IS DISTINCT FROM ARRAY['OPEN', 'IN_REVIEW', 'CONFIRMED']::TEXT[] THEN
        RAISE EXCEPTION
            'Expected app.requirement_status values {OPEN,IN_REVIEW,CONFIRMED}, found %',
            current_values;
    END IF;
END
$$;

CREATE TYPE app.requirement_status_v2 AS ENUM (
    'EXTRACTED',
    'AMBIGUOUS',
    'CLARIFYING',
    'IN_REVIEW',
    'CONFIRMED'
);

ALTER TABLE app.requirement
    ADD COLUMN status_v2 app.requirement_status_v2;

UPDATE app.requirement requirement
   SET status_v2 = (
       CASE
           WHEN requirement.status::TEXT <> 'OPEN'
               THEN requirement.status::TEXT
           WHEN EXISTS (
               SELECT 1
                 FROM app.requirement_revision revision
                WHERE revision.requirement_id = requirement.id
                  AND revision.status = 'PROPOSED'
           )
               THEN 'IN_REVIEW'
           WHEN EXISTS (
               SELECT 1
                 FROM app.requirement_revision revision
                WHERE revision.requirement_id = requirement.id
                  AND revision.status = 'REJECTED'
           )
               THEN 'CLARIFYING'
           WHEN EXISTS (
               SELECT 1
                 FROM app.clarification clarification
                WHERE clarification.requirement_id = requirement.id
           )
               THEN 'CLARIFYING'
           WHEN EXISTS (
               SELECT 1
                 FROM app.ambiguity_issue issue
                WHERE issue.requirement_id = requirement.id
                  AND issue.status = 'OPEN'
           )
               THEN 'AMBIGUOUS'
           ELSE 'EXTRACTED'
       END
   )::app.requirement_status_v2;

ALTER TABLE app.requirement
    ALTER COLUMN status_v2 SET NOT NULL;

ALTER TABLE app.requirement
    DROP CONSTRAINT ck_requirement_confirmation;

ALTER TABLE app.requirement
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE app.requirement
    DROP COLUMN status;

ALTER TABLE app.requirement
    RENAME COLUMN status_v2 TO status;

DROP TYPE app.requirement_status;

ALTER TYPE app.requirement_status_v2
    RENAME TO requirement_status;

ALTER TABLE app.requirement
    ALTER COLUMN status SET DEFAULT 'EXTRACTED'::app.requirement_status;

ALTER TABLE app.requirement
    ADD CONSTRAINT ck_requirement_confirmation CHECK (
        (status = 'CONFIRMED' AND approved_revision_id IS NOT NULL
            AND confirmed_text IS NOT NULL AND confirmed_at IS NOT NULL)
        OR (status <> 'CONFIRMED' AND approved_revision_id IS NULL
            AND confirmed_text IS NULL AND confirmed_at IS NULL)
    );

COMMIT;

SELECT status, COUNT(*) AS requirement_count
  FROM app.requirement
 GROUP BY status
 ORDER BY status;
