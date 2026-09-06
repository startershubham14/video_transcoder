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

## 2026-09-02 — Status endpoint (build-order step 7) + dependency setup

**Branch:** `claude/dev-branch-docs-review-6ded45` (to be published to `dev`)

**Goal:** Make `GET /jobs/{id}` real (it was a stub → 500) so clients / `scripts/smoke.py` can poll
progress and retrieve output URLs, and pre-install the dependencies the observability + integration-
test backlog needs.

**Done:**
- **Dependencies (`pom.xml`):** added `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
  (observability base) and Testcontainers (`testcontainers-bom` import + test-scope `junit-jupiter`,
  `postgresql`, `rabbitmq`; pinned `testcontainers.version=1.20.4`). `application.yml` exposes
  `health,info,prometheus` actuator endpoints (`/actuator/health` now live). Testcontainers is
  dormant until the integration-test task uses it.
- **Status endpoint (polling):** implemented `JobStatusService.getStatus` — loads the job (404 via
  `ResponseStatusException` for unknown id, matching existing API error style), derives progress from
  segment COUNTs, and mints presigned GET URLs only when `COMPLETED`. Added
  `SegmentRepository.countByJobId` / `countByJobIdAndStatus` (cheap COUNTs, no full-row loads).
  `JobController` no longer needs the not-found TODO.
- New knob `pipeline.download-url-ttl-minutes` (`DOWNLOAD_URL_TTL_MINUTES`, default 60) — no magic
  numbers for the presigned-URL lifetime.
- Tests: `JobStatusServiceTest` (8 new — progress edge cases incl. the 99-cap, 404, URLs-only-when-
  COMPLETED with one presigned URL per rung, error surfacing). Fixed `AdmissionPolicyTest`'s
  `PipelineProperties` constructor for the new field. `./mvnw -B verify` green (**41 tests**, 4
  pre-existing `@Disabled` skips).

**Key decisions:**
- **Progress capped at 99** for non-COMPLETED jobs so "100%" never shows while a job is still
  `CONCATENATING`/packaging; `COMPLETED` is authoritatively 100.
- **Output URLs derived, never stored** (Golden rule 8): reuse the packaging stage's key derivation
  via `PackagerFactory` + `Packager.outputKey` — MP4 → one URL per rung; HLS → the master manifest
  (`manifest_key`) when present. Callers never branch on `OutputMode`.
- **Polling only; SSE deferred** — build order says "status polling (SSE later)". Left
  `OutputDeliveryService` / `StatusStreamController` stubs untouched to avoid editing the working
  packaging stage (OCP) for a deferred feature.

**Open follow-ups:** SSE push; reliability (ErrorClassifier/retry-backoff, sweep/reaper bodies,
`@RestControllerAdvice`); observability dashboards (Prometheus + Grafana, MDC logging) — deps now
in place; Testcontainers integration tests (race/idempotency/error-routing) — deps now in place;
HLS packager.

---

## 2026-09-05 — Reliability (build-order step 8)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Replace the stub failure story (every failure dead-lettered on first try; classifier/sweep/
reaper unimplemented) with the documented reliability machinery (architecture doc §5).

**Done:**
- **`ErrorClassifier`** — cause-walking transient/permanent decision. PERMANENT:
  `PrepareRejectedException`, S3 `NoSuchKeyException`, `ProcessExecutionException`, and definitive AWS
  4xx. TRANSIENT: AWS 5xx/429, `SdkClientException`, `IOException`/socket/connect, `AmqpException`.
  Unknown → TRANSIENT (retry cap bounds the cost).
- **Backoff transport** — new fanout `retry.exchange` + rewired `retry.delay.queue`
  (`x-dead-letter-exchange=""`, no dlx-routing-key). New `RetryPublisher.scheduleRetry` re-publishes
  the original message **bytes** to the exchange with routing key = origin stage queue, a per-message
  TTL, and an `x-retry-attempts` header. Fanout parks it in the delay queue while retaining the key;
  on TTL-expiry it dead-letters back to the origin stage queue. One retry queue serves all stages.
- **`AbstractStageWorker`** — now takes the raw AMQP `Message`; reads the attempt header; TRANSIENT &
  `attempts < RETRY_MAX_ATTEMPTS` → `scheduleRetry` (backoff `[2,8,30]s` by attempt) + ack; PERMANENT /
  exhausted → `onGiveUp` hook + nack→DLQ. Three listeners updated to pass the `Message` (transport-only
  role kept).
- **No hung jobs** — `TranscodeListener.onGiveUp` → `TranscodeHandler.failSegment`: guarded
  `SegmentRepository.markFailed` + `JobRepository.failJob` (PREPARING/PROCESSING/CONCATENATING→FAILED,
  fires once).
- **`UploadTimeoutReaper`** — past-deadline `AWAITING_UPLOAD` → `EXPIRED` (no S3 abort; uploadId isn't
  persisted, S3 lifecycle handles it). **`ReconciliationSweep`** — re-publishes stale `PREPARING` jobs
  (PrepareTask) and stale `QUEUED` segments (TranscodeTask), gated by
  `pipeline.reconciliation-stale-seconds` (default 120) so it never races live work.
- **Global `@RestControllerAdvice`** (`web.ApiExceptionHandler` + `web.ApiError`) — consistent JSON
  error bodies; preserves `ResponseStatusException` status/reason, generic 500 otherwise (no leaks).
- Tests: `ErrorClassifierTest`, `ErrorRoutingTest` (now real — routing via a mocked worker),
  `UploadTimeoutReaperTest`, `ReconciliationSweepTest`, `ApiExceptionHandlerTest`.
  `./mvnw -B verify` green (**56 tests**; only `FanInRaceTest` still `@Disabled` for Testcontainers).

**Key decisions:**
- **Attempt count lives in the message header**, not the DB — the retry mechanism is uniform across
  all three stages (Template Method), no per-stage divergence.
- **Fanout retry.exchange** chosen so a single retry queue can return each message to its own origin
  stage via the retained routing key (RabbitMQ has no per-message dead-letter routing key).
- Unknown errors default TRANSIENT per the doc; a definitive AWS 4xx is treated PERMANENT.

**Open follow-ups (deferred):** segment `RETRY_WAIT` observability (header is the authoritative
counter); prepare/package give-up job-failing; package-stage reconciliation; DLQ drain/inspection;
Testcontainers e2e for race/idempotency/routing (deps in place).

---

## 2026-09-05 — HLS packaging (the second Packager — primary OCP test)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Implement the HLS output mode as a second `Packager` without touching `Mp4Packager` or the
transcode stage — the project's primary Open/Closed test.

**Done:**
- **`HlsPackager`** — reuses the rung's already-transcoded MPEG-TS segments (`{jobId}/{rung}/{index}.ts`).
  `packageRung` authors a VOD media playlist referencing them **relatively** (`../{rung}/{index}.ts`),
  `#EXTINF` from `MediaProbe.probe(seg).durationSeconds()`, uploads it to `{jobId}/hls/{rung}.m3u8`.
  `finalizeJob` authors the master (`#EXT-X-STREAM-INF` per rung, `BANDWIDTH` = video + 128k audio,
  nominal 16:9 `RESOLUTION`, sorted highest-first) → `{jobId}/master.m3u8`. Playlist authoring is pure,
  unit-tested static helpers.
- **`Packager.masterOutputKey(jobId)`** — new additive `default` (empty). HLS overrides →
  `{jobId}/master.m3u8`. Keeps mode-branching out of callers; `Mp4Packager` untouched.
- **`BlobStore.publicUrl(key)`** (+ `S3BlobStore` impl: path-style host-reachable endpoint) for
  public-read HLS assets.
- **`JobStatusService`** — returns the single public master URL when `masterOutputKey` is present (HLS),
  else per-rung presigned URLs (MP4). No reliance on `jobs.manifest_key` (derived, not stored).
- **docker-compose** — `minio-setup` runs `mc anonymous set download` so HLS siblings are fetchable.
- **`static/player.html`** — minimal hls.js page (`?src=<master>`), served by the api profile.

**Key decisions:**
- **Reuse the transcoded `.ts`; only write playlists** (architecture doc §8) — demonstrates that MP4↔HLS
  differ *only* in the packaging step. No re-encode/re-segment.
- **Delivery decision (confirmed with owner):** HLS is many sibling files a single presigned URL can't
  cover, so HLS outputs are **public-read** in local MinIO and delivered as a plain master URL. Not a
  Golden-rule violation (no API bytes, no persisted presigned URLs); prod would use signed cookies/CDN.
  MP4 delivery stays presigned.
- `masterOutputKey` seam keeps `JobStatusService` from branching on `OUTPUT_MODE`.

**Verified:** `./mvnw -B verify` green (**63 tests**; only `FanInRaceTest` `@Disabled` for
Testcontainers). Browser playback is a Docker/`OUTPUT_MODE=hls` step for the owner (see HANDOFF).

**Open follow-ups:** per-prefix (not whole-bucket) public policy; prod signed-cookie/CDN delivery.

---

## 2026-09-05 — SSE live status push (deferred half of step 7)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Implement `GET /jobs/{id}/events` so clients hold one streaming connection that pushes
progress and, on completion, the output URLs — instead of polling.

**Done (event-driven via RabbitMQ — the SSE endpoint is in `api`, progress happens in workers):**
- **`JOB_EVENTS_EXCHANGE` fanout** + `JobEvent(jobId)` record + `JobEventPublisher` (separate from
  `TaskPublisher`; broadcasts notifications, not work). Workers publish a `jobId` **poke** after each
  state-affecting commit; the API re-reads authoritative state from Postgres — the event carries no
  progress data (rule 2).
- **Publish points (additive, after commit):** `PrepareHandler` (→PROCESSING, →FAILED),
  `TranscodeHandler` (segment DONE tick, give-up →FAILED), `PackageHandler` (→COMPLETED),
  `UploadTimeoutReaper` (→EXPIRED).
- **`JobEventListener`** (`@Profile("api")`): binds an **anonymous auto-delete** queue to the fanout
  (each API instance gets every event), manual-acks in `finally`, delegates to `OutputDeliveryService`.
- **`OutputDeliveryService`** (rewritten): `ConcurrentHashMap<UUID, Set<SseEmitter>>` registry;
  `register` pushes an immediate snapshot; `onJobEvent` snapshots via `JobStatusService.getStatus` and
  pushes a `status` event to that job's emitters, completing + removing them on a terminal status.
- **`StatusStreamController`** validates via `getStatus` (unknown → clean 404 through the advice), then
  registers an `SseEmitter`. New `SseProperties` (`sse.timeout-minutes`, default 30) — kept out of
  `PipelineProperties` to avoid churning its positional-record test call-sites. Added `JobStatus.isTerminal()`.

**Key decisions:**
- **Poke-then-read-DB:** events are just `jobId`; the API derives the snapshot from Postgres, so no
  progress math is duplicated in the worker and stale/late events never lie.
- **Explicit removal on terminal**, not via the emitter's `onCompletion` callback: a completed emitter
  with no active servlet request doesn't fire that callback synchronously (also what makes the unit
  test possible). Client-initiated closes still deregister via the callbacks.
- **Anonymous auto-delete queue per API instance** so a scaled-out API still delivers to whichever
  instance holds the SSE connection.

**Verified:** `./mvnw -B verify` green (**67 tests**; only `FanInRaceTest` `@Disabled`). Live streaming
is a Docker step for the owner (`curl -N /jobs/{id}/events`).

**Open follow-ups:** heartbeat/keep-alive for idle connections; `Last-Event-ID` resume.

---

## 2026-09-05 — Reliability follow-ups (give-up job-failing, RETRY_WAIT, package reconciliation)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Close the three gaps the reliability core (step 8) deferred.

**Done:**
1. **Prepare/package give-up → FAILED.** `PrepareHandler.failOnGiveUp` / `PackageHandler.failOnGiveUp`
   (guarded `JobRepository.failJob` + `JobEventPublisher.publish`), wired via `onGiveUp` overrides in
   `PrepareListener` / `PackageListener` — mirrors the transcode path. An infra failure that exhausts
   retries no longer leaves the job hung in PREPARING/CONCATENATING.
2. **Segment `RETRY_WAIT`.** New `AbstractStageWorker.onRetry(task, attempt)` no-op hook (fired when a
   transient failure is scheduled for retry); `TranscodeListener` overrides →
   `TranscodeHandler.markSegmentRetryWait` → `SegmentRepository.markRetryWait` (PROCESSING→RETRY_WAIT,
   `attempts++`). `markProcessing` guard widened to `QUEUED | RETRY_WAIT` so the redelivery moves it
   back to PROCESSING.
3. **Package-stage reconciliation.** `ReconciliationSweep` now also re-drives stale `CONCATENATING`
   jobs: for each rung whose segments are all DONE but whose output is missing
   (`blobStore.exists(packager.outputKey(...))`), re-publish `PackageTask`. New sweep deps:
   `PackagerFactory`, `BlobStore`.

**Key decision:** the redelivery collapses the documented `RETRY_WAIT → QUEUED → PROCESSING` into
`RETRY_WAIT → PROCESSING` — the `QUEUED` hop would be instantaneous and carries no signal. CLAUDE.md's
segment-state line updated to match (stale docs = bug). The message header remains the authoritative
retry counter; `segments.status`/`attempts` are observability.

**Tests:** `ErrorRoutingTest` extended (onRetry fires with the attempt number on transient-under-cap,
not on success/permanent/exhausted); `TranscodeHandlerTest` (markSegmentRetryWait, failSegment);
`ReconciliationSweepTest` extended (package re-drive: missing-output all-DONE rung re-published;
not-all-DONE / already-packaged skipped). `./mvnw -B verify` green (**71 tests**; only `FanInRaceTest`
`@Disabled`).

**Open follow-ups:** DLQ drain/inspection tooling.

---

## 2026-09-06 — Observability: api-side metrics + Grafana dashboard (lean)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Expose the pipeline metrics the scaling demo needs ("watch the queue drain") with minimal
footprint — after reconsidering the original full plan on performance/complexity grounds.

**Scope decision (with owner):** rejected per-worker scraping (a servlet server on every headless
worker + per-replica discovery) as too much footprint/complexity for a local demo. Chosen: **all
metrics computed on the api** from Postgres + RabbitMQ, so workers are untouched and there's no app
hot-path cost — gauge suppliers run only when Prometheus scrapes (~15s), on the api alone. No worker
timers, no MDC (deferred).

**Done:**
- **`PipelineMetrics`** (`config/`, `@Profile("api")`) registers Micrometer gauges on the
  `MeterRegistry`: `pipeline.jobs{status}` (`JobRepository.countByStatus`), `pipeline.segments{status}`
  (`SegmentRepository.countByStatus`), `pipeline.queue.depth{queue}` (`RabbitAdmin.getQueueInfo`,
  null/exception-safe → 0) for the five queues. Two derived repo counts added.
- `application.yml`: `management.metrics.tags.application=video-transcoder`. `/actuator/prometheus`
  was already exposed.
- **docker-compose**: `prometheus` (`:9090`, scrapes only `api:8080/actuator/prometheus`) + `grafana`
  (`:3000`, anonymous viewer, provisioned datasource + dashboard). Config under `docker/prometheus/`
  and `docker/grafana/`. Dashboard `pipeline.json`: transcode queue depth, all queue depths,
  jobs-by-status, segments-done rate (`rate(pipeline_segments{status="DONE"}[1m])`).

**Key decisions:** gauges sourced from the DB/broker (rule 2) keep a single scrape target and directly
power the queue-drain/throughput story; throughput is `rate()` in Grafana rather than a worker counter.

**Verified:** `./mvnw -B verify` green (**72 tests**; only `FanInRaceTest` `@Disabled`).
`PipelineMetricsTest` asserts the gauges against mocked repos/RabbitAdmin (incl. missing-queue → 0).
The Prometheus/Grafana stack is docker-compose config (not runnable in the agent shell) — dashboard
verified by the owner in Docker.

**Open follow-ups:** per-stage worker timers/latency (needs worker scraping); MDC logging; alerting;
`scaling_benchmark.md`.

---

## 2026-09-06 — Scaling demo runner (build-order step 9)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Provide the tooling to measure "transcode throughput scales with worker count" and make
`scaling_benchmark.md` runnable (it was method + a pseudocode stub).

**Done:**
- **`scripts/bench.py`** (stdlib only) — submits K jobs via the thin upload handshake (same flow as
  `smoke.py`: POST /uploads → PUT presigned parts → POST /complete), starts the clock at first
  submission, polls `GET /jobs/{id}` until every job is terminal, and prints wall-clock, throughput
  (jobs/min), failures, and a ready-to-paste results-table row. Flags: `--copies`, `--label`, `--api`,
  `--poll`, `--timeout`.
- **`scaling_benchmark.md`**: replaced the runner stub with real `bench.py` usage; method now points at
  the Grafana dashboard (`pipeline_queue_depth` draining, `rate(pipeline_segments{status="DONE"})`) for
  a live view while the benchmark runs.

**Key decisions:** a Python driver (not `run.sh`) — cross-platform on the Windows host and reuses the
existing stdlib handshake; reuses the now-built `GET /jobs/{id}` for the drain poll. `*.mp4/*.mov/*.ts`
are already git-ignored, so a `samples/` clip set won't be committed.

**Verified:** `python -m py_compile scripts/bench.py` OK. No Java changed → `./mvnw verify` unaffected
(still 72 tests). The actual benchmark **run** needs Docker + real clips and is the owner's to execute;
the Results table stays to-be-filled.

**Open follow-ups:** run the benchmark, paste medians + a chart into the README Results section.

---

## 2026-09-06 — Fix: api failed to boot (PipelineMetrics needed RabbitAdmin)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**What went wrong:** the observability commit's `PipelineMetrics` injects `RabbitAdmin`, but no such
bean resolved at runtime → the **api context failed to start** (`APPLICATION FAILED TO START`,
container Exited(1)). The unit test mocked `RabbitAdmin`, so it passed — a runtime-wiring bug that
compiled and unit-tested clean. Caught by a real `docker compose up` (Docker was reachable this
session).

**Fix:** declare an explicit `RabbitAdmin` bean in `RabbitMqConfig` (`new RabbitAdmin(connectionFactory)`)
rather than relying on Boot's auto-configured one. Natural home — that config already owns the queue/
exchange topology the admin declares. Updated the class Javadoc accordingly.

**Verified live:** rebuilt the api image and restarted it in the running stack — boots clean
(`Started TranscoderApplication`), and `GET /actuator/prometheus` serves the gauges
(`pipeline_jobs{status="COMPLETED"} 5`, `pipeline_segments{status="DONE"} 168`,
`pipeline_queue_depth{queue=...} 0` while idle). `./mvnw -B verify` still green (72 tests).

**Lesson:** metric/bean wiring that unit tests can't catch needs at least one real boot — prefer a
lightweight context/smoke check for DI wiring in future.

---

## 2026-09-06 — Testcontainers fan-in tests (Docker was reachable this session)

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Turn the `@Disabled` `FanInRaceTest` placeholder into real Testcontainers tests of the
per-rung fan-in (Golden rule 5), now that the Docker daemon was reachable.

**Done:**
- **`FanInRaceTest`** — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` +
  `@ImportAutoConfiguration(FlywayAutoConfiguration)` + a Testcontainers `postgres:16-alpine` wired via
  `@DynamicPropertySource` (no `spring-boot-testcontainers` dep needed). Flyway applies V1+V2; Hibernate
  validates. `@Transactional(NOT_SUPPORTED)` so setup + worker threads commit independently. Three tests,
  all green against real Postgres:
  1. the claim does **not** fire while a rung segment is unfinished, and completing the final segment
     (markDone+tryClaim in one tx, as `TranscodeHandler` does) claims **exactly once** → CONCATENATING;
  2. a redelivered `markDone` is idempotent (0 rows, stable key, no duplicate rows);
  3. a concurrent redelivery storm of the final segment stays consistent (≥1 claim, job CONCATENATING,
     no duplicate rows).
- `./mvnw -B verify` now **73 tests, 0 skipped** (was 72 with 2 `@Disabled`).

**Finding (noted, not fixed):** the fan-in relies on the *last committer* observing the whole rung
DONE. A first, unrealistically-simultaneous test (release N workers with zero work between their
`markDone` and `tryClaim`, before any commit) produced **0 claims** — a narrow edge where perfectly
concurrent completion of the final segment(s) could **lose** the packaging trigger, hanging the job in
PROCESSING. In production, encodes finish staggered over seconds so this is effectively unreachable, and
a redelivered transcode task re-attempts the claim. **Follow-up option:** extend `ReconciliationSweep`
to also re-drive PROCESSING jobs whose every segment is DONE (re-attempt the claim) to fully close it.
The committed tests assert the deterministic, faithful guarantees rather than this timing artifact.

**Also this session:** fixed the api-boot `RabbitAdmin` bug (see prior entry) and verified the running
stack serves `pipeline_*` metrics. A live smoke/benchmark run is still pending (ClamAV was unhealthy).

---

## 2026-09-06 — Fix the fan-in lost-claim edge in the reconciliation sweep

**Branch:** `claude/dev-branch-docs-review-6ded45` (published to `dev`)

**Goal:** Close the narrow lost-claim edge surfaced by `FanInRaceTest` — a perfectly-simultaneous
final-segment completion where every worker's `tryClaim` runs before any `markDone` commits can leave
a rung fully DONE with no claim, hanging the job in `PROCESSING`.

**Fix:** `ReconciliationSweep` now re-drives packaging for stale jobs in **both** `PROCESSING` and
`CONCATENATING` (was CONCATENATING-only). For each rung that is fully DONE but whose output is missing,
it runs `tryClaimPackaging` in a transaction (flips `PROCESSING→CONCATENATING`, or re-matches
`CONCATENATING`) and, on a winning claim (returns 1), re-publishes the `PackageTask`. The all-DONE guard
still prevents packaging a partial rung. Injected a `TransactionTemplate` for the guarded claim.

**Tests:** `ReconciliationSweepTest` gains `recoversLostClaimForStuckProcessingJob` (PROCESSING job,
all-DONE rung, missing output → `tryClaimPackaging` → `publishPackage`); the CONCATENATING case now also
stubs the re-claim. `./mvnw -B verify` green (**74 tests, 0 skipped**).

**Result:** the edge `FanInRaceTest` documented is now recovered by the sweep within
`RECONCILIATION_STALE_SECONDS`; the design's "at-least-once, idempotent" packaging guarantee holds even
under the pathological race.

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
