-- Initial schema for the transcoding pipeline.
-- Mirrors docs/architecture-and-workflow.md §9. jobs + segments are the source of
-- truth for state; the per-rung fan-in is an atomic UPDATE against these tables.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

CREATE TYPE job_status AS ENUM
  ('AWAITING_UPLOAD','PREPARING','PROCESSING','CONCATENATING','COMPLETED','FAILED','EXPIRED');
CREATE TYPE segment_status AS ENUM
  ('QUEUED','PROCESSING','DONE','RETRY_WAIT','FAILED');

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       TEXT UNIQUE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE jobs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    status            job_status NOT NULL DEFAULT 'AWAITING_UPLOAD',
    source_key        TEXT,                 -- s3: {id}/source.mp4
    -- probed metadata (filled in prepare, after ffprobe)
    source_width      INT,
    source_height     INT,
    duration_seconds  NUMERIC,
    fps               NUMERIC,
    source_codec      TEXT,
    size_bytes        BIGINT,
    -- output
    manifest_key      TEXT,                 -- master .m3u8 (HLS); NULL for MP4
    error_reason      TEXT,
    upload_deadline   TIMESTAMPTZ,          -- for the timeout reaper
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_status   ON jobs(status);
CREATE INDEX idx_jobs_deadline ON jobs(upload_deadline) WHERE status = 'AWAITING_UPLOAD';

CREATE TABLE segments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    rung                TEXT NOT NULL,       -- '720p' | '480p' | '360p'
    segment_index       INT  NOT NULL,       -- ordering for concat/playlist
    status              segment_status NOT NULL DEFAULT 'QUEUED',
    attempts            INT  NOT NULL DEFAULT 0,   -- checked against N = 3
    source_segment_key  TEXT NOT NULL,
    output_segment_key  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- idempotency: a replayed fan-out cannot create duplicate rows
    UNIQUE (job_id, rung, segment_index)
);
CREATE INDEX idx_segments_job_rung ON segments(job_id, rung);
