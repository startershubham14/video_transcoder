# DEVLOG

Append-only task log (see CLAUDE.md → Working agreement). Newest entries at the bottom.

---

## 2026-08-31 — Scaffold empty classes + structural review response

**Branch:** `claude/project-setup-empty-classes-86ad1c` (work published to `dev`)

**Goal:** Stand up the Spring Boot skeleton (empty/stub classes) for the pipeline, following
the design docs and, once supplied, `CLAUDE.md`.

**Steps & key decisions:**
- Built the Maven project (Spring Boot 3.3.5, Java 21) with the package-by-stage layout
  (`upload`, `prepare`, `transcode`, `packaging`, `job`, `storage`, `messaging`, `config`)
  under base package `dev.shubham.transcoder`. Stub classes carry Javadoc + Spring
  stereotypes + constructor wiring; bodies throw `UnsupportedOperationException`.
- **CI fix:** the workflow called `./mvnw` but no Maven wrapper exists (and none can be
  generated without `mvn` locally/CI). Switched CI to the runner's preinstalled `mvn`, and
  replaced the `@SpringBootTest` context-load test with `RungTest` so `verify` passes without
  Postgres/RabbitMQ. *Follow-up: add a real Maven wrapper and restore `./mvnw` when `mvn` is
  available.*
- **Review response (structural):** added `db/migrations/V1__init.sql` (schema §9, incl.
  `job_status`/`segment_status` enum types; mapped on entities via `PostgreSQLEnumJdbcType`)
  to unblock boot; introduced the `Packager` Strategy + `PackagerFactory`; `AbstractStageWorker`
  Template Method + `ErrorClassifier`; ports/adapters (`BlobStore`, `MediaProbe`,
  `VirusScanner`, `Splitter`, `Transcoder`); moved `User` to a `user/` package; added
  `@Disabled` placeholders for the fan-in race, idempotency, and error-routing must-tests.
- Added the authoritative `CLAUDE.md`, `scaling_benchmark.md`, and this `DEVLOG.md`.

**Gotchas:**
- `dev` was never pushed to `origin`; the docs + scaffold commits existed only locally. The
  work branch was advanced onto `dev`'s tip and published so it fast-forwards cleanly.
- No Maven available in the dev environment → cannot compile-verify locally; CI is the first
  real compile. Structural checks (package/type/path + seam wiring) run instead.

**Open follow-ups:**
- **Conform the scaffold to CLAUDE.md naming** (pending): orchestrators `*Service` →
  `<Stage>Handler`; listeners → `<Stage>Listener` (transport) separate from handlers;
  adapters → technology+port names (`S3BlobStore`, `FfmpegTranscoder`, `FfprobeMediaProbe`,
  `ClamAvVirusScanner`, `FfmpegSplitter`); `OutputLadderService` → `LadderPolicy`; entities
  to factory + intent-method transitions (no public `setStatus`).
- **Runtime topology** (deferred): `docker-compose.yml`, `Dockerfile`, nginx — needed for the
  `--scale transcode-worker=N` scaling demo.
- Add the Maven wrapper; wire real RabbitMQ topology beans; first vertical slice
  (one video → one rung → MP4).

---

## 2026-08-31 — Conform scaffold to CLAUDE.md naming & entity conventions

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Goal:** Close the naming/entity divergences between the scaffold and the now-authoritative
`CLAUDE.md` while everything is still stubs (cheapest time; no setter is called yet). Resolves
the "conform to CLAUDE.md naming" follow-up from the previous entry.

**Steps & key decisions:**
- **Adapters → technology+port names:** `S3StorageService`→`S3BlobStore`,
  `FfprobeService`→`FfprobeMediaProbe`, `ClamAvScanner`→`ClamAvVirusScanner`,
  `VideoSplitter`→`FfmpegSplitter`, `SegmentTranscoder`→`FfmpegTranscoder`. Injections already
  used the ports, so blast radius was each impl file + one `@link` per port.
- **Stage split by role:** `<Stage>Service`→`<Stage>Handler` (logic), `<Stage>Worker`→
  `<Stage>Listener` (transport, still `extends AbstractStageWorker`). `PackagingService`→
  `PackageHandler`.
- **`OutputLadderService`→`LadderPolicy`** (Strategy) + real impl `FixedLadderPolicy`
  (rungs strictly below source height, no upscaling) — first non-stub logic, unit-tested.
- **`JobService` split:** `AdmissionPolicy` (`canAdmit`) + `JobStatusService` (`getStatus`).
  `UploadService`→`UploadHandler`. `ClamAvConfig`→`ClamAvProperties`. Controller methods →
  action names (`getJobStatus`, `createUpload`, `completeUpload`).
- **Entities → factory + guarded transitions:** `Job.create`/`Segment.create`/`User.create`;
  guarded `transitionTo` + intent verbs (`markProcessing`, `markDone`, `failWith`, …); removed
  all public setters incl. `setStatus`. Ids via Hibernate `@UuidGenerator`; timestamps via
  `@CreationTimestamp`/`@UpdateTimestamp`. **Decision:** used app-side `@UuidGenerator` (the
  robust, canonical Hibernate 6 form) rather than a fragile DB-`gen_random_uuid()` read-back
  mapping I couldn't verify without a live DB; the migration keeps `gen_random_uuid()` as the
  column default. Revisit if strict DB-generation is required.
- **`SegmentRepository.tryClaimPackaging`** now carries the real native atomic fan-in SQL
  (was a TODO). `BlobStore.headObjectSize`→`objectSize`.
- Added pure-logic unit tests: `JobTransitionTest`, `FixedLadderPolicyTest`.

**Gotchas:** no Maven locally → CI (`mvn -B verify`) is still the first real compile;
structural check + old-name/setter sweep run clean (66 java files).

**Open follow-ups:** unchanged from the previous entry except the naming-conformance item is
now **done** — remaining: runtime topology (`docker-compose`/`Dockerfile`/nginx), Maven
wrapper, RabbitMQ topology beans, first vertical slice.

---

## 2026-08-31 — Strict DB-side UUID generation + migration verified on real Postgres

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Goal:** Per request, make id generation strictly DB-side (not app-side), and actually
exercise `V1__init.sql` against a real Postgres.

**Changes:**
- Entities `Job`/`Segment`/`User`: replaced Hibernate `@UuidGenerator` (app-side) with
  `@Generated(event = EventType.INSERT)` + `@Column(name="id", updatable=false)`, so the DB's
  `gen_random_uuid()` default produces the id and Hibernate reads it back (INSERT … RETURNING).
- **Migration fix:** dropped `CREATE EXTENSION pgcrypto` — `gen_random_uuid()` is core since
  Postgres 13, so the extension is unnecessary and it also required a privilege the app DB user
  doesn't have (`dbuser123` got "permission denied to create extension").

**Verification (real Postgres 18.4, local, user `dbuser123`):**
- Created a throwaway db owned by the user, ran `V1__init.sql`: all types/tables/indexes created.
- Inserted rows with **no id** → DB generated UUIDs (confirmed via `RETURNING`); defaults
  `jobs.status=AWAITING_UPLOAD`, `segments.status=QUEUED`; `id` is `uuid`, `status` columns use
  the `job_status`/`segment_status` enum types; the atomic fan-in `UPDATE` parses and runs.
- Dropped the throwaway db — zero footprint. (Credentials were a local throwaway; not committed.)

**Still unverified here:** the Hibernate side (that `@Generated` on the id reads back correctly
at boot) needs a Spring run / Testcontainers — no Maven in this environment. The DB half is proven.

---

## 2026-08-31 — Compose skeleton (build-order step 1)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Done:**
- `Dockerfile` (multistage: wrapper build → temurin-jre runtime with FFmpeg, non-root). Same
  image runs api/worker/transcode; `SPRING_PROFILES_ACTIVE` selects the role. `.dockerignore` added.
- `docker-compose.yml`: postgres, rabbitmq(+mgmt), minio(+mc bucket init), clamav, and app
  services `api` / `worker` / `transcode-worker` (scalable) with healthchecks + depends_on.
  Works out of the box against local MinIO defaults.
- Implemented `RabbitMqConfig` (declares prepare/transcode/concat + dead-letter + retry-delay
  queues, stage queues DLX→dead-letter, Jackson JSON converter) so the broker topology exists
  on boot. Profile separation: prepare/package listeners `@Profile("worker")`, transcode
  `@Profile("transcode")`, reaper/sweep `@Profile("api")` (and made no-op so timers don't spam).
  Worker/transcode profiles set web-application-type=none.
- `.env.example` updated with MinIO local defaults.

**Verified:** `./mvnw -B verify` green (14 tests). Booted the packaged app (api profile,
RabbitMQ excluded) against local Postgres → Flyway applied V1 and Hibernate `validate` passed
with no errors — the DB-side `@Generated` UUID + Postgres-enum mappings validate at real boot.
Docker itself couldn't run here (no daemon); `docker compose up` is for the user to run.

**Remaining:** implement stage logic (upload → prepare → transcode → package), TaskPublisher,
S3Config beans, retry/DLQ wiring; first vertical slice; Testcontainers integration test.

---

## 2026-08-31 — Upload flow (build-order step 3)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Done (implemented real behaviour, no longer stubs):**
- `S3Config` — builds `S3Client` + `S3Presigner` (lazy; endpoint override + path-style for MinIO).
  `StorageProperties` binds `aws.*` (region, bucket, endpoint, part-size). `S3BlobStore` fully
  implemented: multipart init + presigned part PUTs, complete/abort, headObject size, get/put,
  presigned GET. `initiateMultipartUpload` now returns `PresignedMultipartUpload{uploadId, urls}`.
- `TaskPublisher` — sends task records to stage queues via RabbitTemplate (JSON converter).
- `UploadHandler` — `createUpload` (declared-size gate, get-or-create default user, create job
  AWAITING_UPLOAD, derive `{jobId}/source.mp4`, initiate multipart, presign N parts) and
  `completeUpload` (CompleteMultipartUpload authoritative, size cap, guarded → PREPARING,
  enqueue-after-commit via afterCommit synchronization). Added `application.yml`
  `aws.s3.part-size-bytes` (16 MiB). Unit test for part-count.

**Decisions / caveats:**
- v1 has no auth → one default user (`default@local`, get-or-create). TODO real auth.
- uploadId not persisted (schema stays minimal); reaper marks EXPIRED and S3 lifecycle aborts
  incomplete multiparts. Abort-by-API would need to store the uploadId.
- Known MinIO/compose gotcha: presigned URLs carry the internal `minio:9000` host, not reachable
  from a browser/host outside the compose network — fine for server-side/in-network testing.

**Verified:** `./mvnw -B verify` green (17 tests). Full boot now needs RabbitMQ (TaskPublisher →
RabbitTemplate), so end-to-end runs via `docker compose up`; couldn't boot-test here (no broker).

**Remaining:** prepare worker (probe/scan/split/fan-out), transcode + fan-in, packaging.

---

## 2026-08-31 — Prepare worker (build-order step 4)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Done (stage 1 works end-to-end; first stage consumer):**
- Worker ack skeleton: `AbstractStageWorker.execute(task, Channel, deliveryTag)` — manual ack
  on success, `basicNack(requeue=false)` → DLX on failure (TODO: transient→retry-delay via
  ErrorClassifier in the reliability step). All 3 listeners now take Channel + DELIVERY_TAG.
- New `media/` package: `ProcessRunner` (one ProcessBuilder wrapper — timeout, concurrent
  stdout/stderr drain, exit-code check) + `FfmpegCommandBuilder` (argv, no string concat).
- Adapters implemented: `FfprobeMediaProbe` (ffprobe JSON → ProbeResult; bad input =
  PrepareRejectedException), `ClamAvVirusScanner` (clamd INSTREAM socket), `FfmpegSplitter`
  (segment muxer `-c copy` → MPEG-TS segments).
- `PrepareHandler`: idempotent (skip if not PREPARING) → download → probe → limit gate → scan →
  ladder → split → upload source segments (`{jobId}/segments/{i}.ts`) → (txn) insert Segment
  rows per rung + markProcessing → fan out TranscodeTask per segment after commit. Permanent
  input failures (`PrepareRejectedException`) fail the job (acked); infra errors → DLQ. Temp
  work dir cleaned in finally.

**Verified:** `./mvnw -B verify` green (24 tests; new: ffprobe-parse, limit-gate, command-builder).
Full boot/e2e needs the stack (ffmpeg in worker image, clamav, rabbit) → `docker compose up`;
couldn't run here. Expected e2e state: segments rows + job PROCESSING; transcode tasks DLQ
(transcode stage not built yet).

**Remaining:** transcode stage + atomic fan-in (`tryClaimPackaging`); packaging (MP4→HLS);
transient retry-delay/ErrorClassifier backoff.

---

## 2026-08-31 — Transcode worker + atomic fan-in (build-order step 5)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Done (stage 2 + the headline coordination mechanism):**
- `FfmpegTranscoder.encode`: scale to rung + H.264/AAC → MPEG-TS (via ProcessRunner + FfmpegCommandBuilder).
- `Rung.fromLabel`; `SegmentRepository.markProcessing` + `markDone(id, outputKey)` (bulk, idempotent).
- `TranscodeHandler`: idempotent (already-DONE → re-attempt claim); markProcessing → download →
  encode → upload `{jobId}/{rung}/{index}.ts` → (txn) `markDone` + `tryClaimPackaging`; the winner
  publishes `PackageTask(jobId,rung)` after commit. No edits to any working stage (OCP).

**Verified:** `./mvnw -B verify` green (27 tests; new: Rung.fromLabel, encode-argv, output-key).
**Atomic fan-in proven against live local Postgres:** two workers finishing the last two segments
of a rung → exactly ONE claim returns 1, job → CONCATENATING (the incomplete-rung worker gets 0).
(Testcontainers race test deferred to the integration-test step; Docker can't run in this shell.)

**Remaining:** packaging stage (`PackageHandler` MP4 concat → COMPLETED, then HLS); Testcontainers
integration tests; retry-delay/ErrorClassifier backoff; status endpoint progress.

---

## 2026-08-31 — Packaging stage: MP4 milestone (build-order step 6)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

**Done — the pipeline now runs fully end-to-end (upload → COMPLETED):**
- `Packager` seam gains `outputKey(jobId, rung)` (strategy owns its key format). `Mp4Packager`
  implemented: ffmpeg concat demuxer (`-f concat -c copy`) over the rung's ordered encoded
  segments → upload `{jobId}/{rung}.mp4`. `HlsPackager` gains its outputKey; body still stub.
- `BlobStore.exists`; `SegmentRepository.findByJobIdAndRungOrderBySegmentIndexAsc` +
  `findDistinctRungs`; `JobRepository.tryComplete` (guarded CONCATENATING→COMPLETED).
- `PackageHandler`: download rung's DONE segments → `packager.packageRung` → **job-completion
  fan-in** (when every rung's output exists: `finalizeJob` then guarded `tryComplete`, once).
  Resolves the strategy via `PackagerFactory` — never branches on mode. No working stage edited.

**Verified:** `./mvnw -B verify` green (31 tests; new PackagingTest: keys, concat list + argv).
Guarded completion proven against live Postgres: two concurrent last-rung completions → exactly
one `tryComplete` returns 1, job → COMPLETED.

**Remaining:** HLS packager (second Packager) + `hls.js` page; Testcontainers integration/e2e
tests; retry-delay/ErrorClassifier backoff; status endpoint progress; scaling demo; README.

---

## 2026-08-31 — Local e2e enablers: presign-endpoint split + smoke script

**Branch:** `claude/project-setup-empty-classes-86ad1c` (published to `dev`)

- **Presign-endpoint split:** `StorageProperties.S3.publicEndpoint` (+`presignEndpoint()`); the
  `S3Presigner` now signs against the public endpoint if set, so presigned URLs are reachable
  from the host (`localhost:9000`) while the app keeps using `minio:9000`. compose sets
  `AWS_S3_PUBLIC_ENDPOINT=http://localhost:9000` by default.
- **`scripts/smoke.py`** (stdlib): drives POST /uploads → PUT presigned parts → POST /complete
  to kick a real clip through the pipeline. Watch via Postgres + RabbitMQ/MinIO UIs (status
  endpoint is step 7, not built yet).

Docker daemon is not reachable from the agent shell, so the actual `docker compose up` run is
the user's; build stays green (`./mvnw verify`, 31 tests).

---

## 2026-09-01 — End-to-end verified in Docker (MP4 milestone reached)

**Branch:** `claude/project-setup-empty-classes-86ad1c` (on `dev`)

Ran the full stack via `docker compose up` and pushed real clips through with `scripts/smoke.py`:
small generated 1080p clip, the 147 MB Simpsons trailer, and a larger video — all reached
`COMPLETED` with playable `{jobId}/{rung}.mp4` (720/480/360) in MinIO. Upload → prepare →
transcode (parallel + atomic fan-in) → package all confirmed working against real
Postgres/RabbitMQ/MinIO/ClamAV.

**Three real bugs the shakedown surfaced (all fixed):**
1. AdmissionControlInterceptor/AdmissionPolicy were stubs that threw → every POST /uploads 500'd.
   Implemented the in-flight cap (429 over cap).
2. ClamAV default StreamMaxLength (25 MB) closed the socket on large uploads (Broken pipe).
   Mounted a clamd.conf raising limits to the ~2 GB input cap.
3. Native Postgres enum columns + Hibernate bulk JPQL updates cast literals as ::SegmentStatus
   (Java class name) ≠ snake_case DB type → transcode markProcessing/markDone failed. Switched
   status to varchar + @Enumerated(STRING) (V2 migration); native fan-in SQL unaffected.

Also added the presign-endpoint split (host-reachable presigned URLs), API_PORT override, and
scripts/smoke.py as a reusable e2e driver.

**Build-order steps 1–6 complete.** Remaining: HLS (2nd Packager) + hls.js page; status endpoint
(GET /jobs/{id} progress + presigned URLs); reliability (retry-delay/ErrorClassifier, reconciliation
sweep, timeout reaper bodies); Testcontainers integration tests; scaling benchmark; README polish.

---

## Backlog — Observability & operability (later tasks, requested)

**Monitoring dashboard / service status**
- Add Spring Boot **Actuator + Micrometer** (`/actuator/health`, `/actuator/prometheus`); expose
  per-stage metrics (queue depth, jobs/min, per-stage latency, success/failure counts) — doubles
  as the scaling-benchmark measurement (CLAUDE.md → Operations & observability).
- Add **Prometheus + Grafana** services to docker-compose with a dashboard: all-services health
  (compose healthchecks already exist), queue depth draining as workers scale, throughput.
  Optionally a minimal status page. RabbitMQ mgmt UI + MinIO console already give partial view.

**Logging** (partially present — basic SLF4J error logs exist)
- Structured logging with **MDC context** (jobId always; segmentId/rung in workers) per CLAUDE.md;
  JSON/structured output; sane levels; never log secrets or presigned URLs. Currently handlers log
  ad hoc without consistent MDC.

**Error handling** (partial — gaps to close)
- Present: workers catch → `nack(requeue=false)` → DLQ; prepare marks job FAILED on bad input;
  API throws `ResponseStatusException` (413/404/409) + admission 429.
- Missing: transient-vs-permanent **retry with backoff** (retry-delay queue via `ErrorClassifier`
  — the AbstractStageWorker TODO); a global `@RestControllerAdvice` mapping domain exceptions to
  consistent JSON error bodies; reconciliation sweep + timeout reaper bodies (recover stuck jobs /
  expire abandoned uploads); DLQ drain/inspection tooling.
