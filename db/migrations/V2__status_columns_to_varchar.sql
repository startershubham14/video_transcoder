-- Store job/segment status as varchar instead of native Postgres enum types.
--
-- Native enum columns require a per-type SQL cast (?::type). In bulk JPQL updates Hibernate
-- names that cast after the Java class (e.g. ::SegmentStatus), which does not match the
-- snake_case DB type names, so updates failed with: type "segmentstatus" does not exist.
-- varchar + @Enumerated(STRING) persists the enum name and needs no cast; the native fan-in
-- SQL keeps working (plain string literals compared to a text column).

-- The partial index predicate references the job_status type; drop it before the column
-- change and recreate it afterwards against the varchar column.
DROP INDEX idx_jobs_deadline;

ALTER TABLE jobs     ALTER COLUMN status DROP DEFAULT;
ALTER TABLE jobs     ALTER COLUMN status TYPE varchar(32) USING status::text;
ALTER TABLE jobs     ALTER COLUMN status SET DEFAULT 'AWAITING_UPLOAD';

ALTER TABLE segments ALTER COLUMN status DROP DEFAULT;
ALTER TABLE segments ALTER COLUMN status TYPE varchar(32) USING status::text;
ALTER TABLE segments ALTER COLUMN status SET DEFAULT 'QUEUED';

CREATE INDEX idx_jobs_deadline ON jobs(upload_deadline) WHERE status = 'AWAITING_UPLOAD';

DROP TYPE job_status;
DROP TYPE segment_status;
