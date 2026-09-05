# CLAUDE.md

Project conventions and guardrails for AI-assisted development. Read this before writing
or changing code. When a request conflicts with a **Golden rule** below, stop and flag it
rather than silently working around it.

---

## Working agreement (mandatory process)

Non-negotiable process rules for any agent (or human) working in this repo. These apply to
**every** task, before and after writing code.

### Branching — never work on `main`
- **NEVER commit or push to `main`.** `main` is integration-only and should be protected.
- Start every task from the latest main on a dedicated branch:
  ```bash
  git checkout main && git pull
  git checkout -b <type>/<short-description>   # type: feat|fix|docs|refactor|test|chore
  ```
- Do all work on that branch. Reach `main` only via a pull request.
- One task = one branch = one focused PR.

### Understand before changing — never assume
- Before writing code, **read the relevant sources**: this CLAUDE.md, the existing code in
  the module you're touching, the migrations, and the config. Do not guess how a component
  behaves — **refer to the documentation and the actual code**.
- If behavior is undocumented or ambiguous, **STOP and ask / flag it.** Do not invent an
  assumption and build on it — a wrong assumption propagates into everything downstream.
- Verify against the real schema/code, not from memory of "how these systems usually work."

### Document every step — task log
- Maintain an **append-only `DEVLOG.md`** at the repo root. For each task record: date,
  branch, goal, the steps taken, key decisions **and why**, anything that went wrong and how
  it was fixed, and any follow-ups left open.
- Write the plan **before** coding; update the log **as you go**, not reconstructed afterward.
- Purpose: traceability and **not repeating past mistakes** — check the log for prior
  gotchas before starting related work.
- Commits: small, focused, imperative messages stating **what** changed and **why**.

### Update documentation after changes — docs are part of the change
- In the **same PR** that changes behavior, update whatever it affects: **CLAUDE.md**
  (conventions, invariants, config), the **README** (architecture summary / diagrams),
  **`.env.example`** (new knobs), and **DEVLOG.md**.
- If a change would contradict a Golden rule or a documented decision, that is a signal to
  **stop and confirm** — never let the code silently diverge from the docs.
- **Stale documentation is treated as a bug.**

### Definition of done (every task)
1. Code + tests written and passing on the branch.
2. Docs updated (CLAUDE.md / README / `.env.example`) to match the change.
3. `DEVLOG.md` entry added.
4. PR opened into `main` with a clear description. Nothing committed directly to `main`.

---

## Project

A distributed video-transcoding pipeline. Ingests a video, splits it into keyframe-aligned
segments, transcodes them in parallel across a worker pool, and packages the result into an
MP4 ladder (milestone) then adaptive-bitrate HLS (goal). The focus is the **distributed
job-processing system**, not the transcoding (FFmpeg does that).

Runs entirely locally via `docker-compose`. Learning project — favour clarity and correct
distributed-systems patterns over cleverness or premature optimization.

## Stack

- **Java 21 + Spring Boot** — Spring Web (API), Spring AMQP (messaging), Spring Data JPA.
- **RabbitMQ** — task broker. NOT Kafka (this is a task queue, not an event stream).
- **PostgreSQL** — source of truth for all job/segment state.
- **AWS S3** (SDK v2) — object storage. MinIO locally via `AWS_S3_ENDPOINT` if set.
- **ClamAV** (`clamd`) — malware scan in the prepare stage.
- **FFmpeg / ffprobe** — invoked via `ProcessBuilder`, never a Java media library.
- **Docker / docker-compose** — one codebase; API and workers differ only by Spring profile.
- **Flyway** — DB migrations.

---

## Golden rules (architectural invariants — do not violate)

1. **Bytes never flow through the API.** Clients upload directly to S3 via presigned
   multipart URLs and download via presigned GET URLs. The API and queue carry only small
   control messages. Never add an endpoint that streams video bytes through the app.
2. **PostgreSQL is the single source of truth for state.** The queue only *dispatches*
   work. "Is the job done?" is answered by querying segment rows, never by queue state.
3. **Enqueue only AFTER the DB commit.** At every hand-off (`/complete`→prepare,
   prepare→fan-out, last-segment→concat): commit state to Postgres first, then publish to
   RabbitMQ. Never publish inside an uncommitted transaction.
4. **Workers are idempotent.** Any task may be redelivered. Re-running a task must be safe:
   check current state, use deterministic S3 keys, overwrite rather than duplicate. No task
   may assume it runs exactly once.
5. **Fan-in is a per-rung atomic claim.** Completion is detected with a single conditional
   `UPDATE ... WHERE NOT EXISTS (unfinished segment in this rung)`. NEVER read-count-then-
   update — that has a check-then-act race. Exactly one worker per rung triggers packaging.
6. **Segments start on keyframes.** Splitting uses FFmpeg's segment muxer with `-c copy`
   (keyframe-snapped). Target length is a *target*, not exact. Segment length is config,
   never hardcoded.
7. **Never trust input.** Validate everything server-side: `CompleteMultipartUpload` (not
   the client) confirms upload; `ffprobe` confirms validity/duration; ClamAV scans before
   transcoding; enforce size/duration limits. Reject, don't assume.
8. **Never persist presigned URLs.** They expire. Store the S3 **key**; mint a fresh
   presigned URL on demand at read time.
9. **Secrets never enter the repo.** Credentials come from env only (`.env`, git-ignored).
   No keys in code, `docker-compose.yml`, or committed files. Use a least-privilege IAM
   user scoped to one bucket.

---

## Pipeline (three stages, each its own queue + `@RabbitListener`)

`prepare` → `transcode` → `concat/package`. Keep every stage OFF the HTTP request thread.

- **prepare** (few consumers): download source → `ffprobe` → ClamAV scan → enforce limits →
  keyframe split → insert segment rows (per rung) → set `total_segments` → fan-out transcode
  tasks → job `PROCESSING`.
- **transcode** (scaled consumers): download segment → FFmpeg encode to rung → upload →
  mark segment `DONE` → attempt per-rung atomic fan-in claim → ack.
- **concat/package** (per rung): stitch to MP4 (milestone) or package to HLS `.ts` + `.m3u8`
  (goal) → upload outputs (+ master `.m3u8` for HLS) → when all rungs done, job `COMPLETED`.

Prepare and transcode have **separate consumer pools** so a transcode backlog can't starve
new jobs (head-of-line-blocking prevention).

## Job / segment states

- Job: `AWAITING_UPLOAD → PREPARING → PROCESSING → CONCATENATING → COMPLETED` (or `FAILED`,
  `EXPIRED`).
- Segment: `QUEUED → PROCESSING → DONE` (or `RETRY_WAIT`, `FAILED`).
- Every state transition is a DB write. The API computes progress by reading these.

---

## Coding conventions

- **Package by feature/stage**, not by layer: `upload`, `prepare`, `transcode`, `packaging`,
  `job`, `storage`, `messaging`, `config`. Avoid a monolithic `service`/`controller` split.
- **Constructor injection only.** No field `@Autowired`.
- **DTOs at the API boundary**, JPA entities never leaked to controllers.
- **Immutability**: prefer `record` for DTOs and messages; `final` fields.
- **No business logic in controllers** — they validate input and delegate.
- **FFmpeg/ffprobe**: wrap `ProcessBuilder` in one place (`storage`/`media` util). Always
  set timeouts, capture stderr, check exit codes, clean up temp files in `finally`.
- **Time**: `Instant`/`TIMESTAMPTZ` everywhere; UTC only.
- **IDs**: UUIDs, generated by the DB (`gen_random_uuid()`).
- **Logging**: SLF4J, structured, always include `jobId` (and `segmentId`/`rung` in workers).
  No secrets or presigned URLs in logs.
- **Naming**: see the dedicated **Naming conventions** section below. In short — names
  encode a thing's *architectural role*, so the class list reads as the design.

### Naming conventions

Names must encode a thing's **role in the architecture** — port vs adapter, handler vs
listener, entity vs DTO. Someone should know what a class *is* from its name alone.

**Casing:** classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE`;
DB tables/columns `snake_case`; packages lowercase, no underscores.

**Classes by role**
- **Ports (interfaces):** capability name, no suffix, no `I` prefix — `BlobStore`,
  `Transcoder`, `MediaProbe`, `Packager`, `VirusScanner`.
- **Adapters (impls):** technology + port — `S3BlobStore`, `FfmpegTranscoder`,
  `FfprobeMediaProbe`, `Mp4Packager`, `HlsPackager`, `ClamAvVirusScanner`.
- **Stage handlers (orchestration + state):** `<Stage>Handler` — `PrepareHandler`,
  `TranscodeHandler`, `PackageHandler`.
- **RabbitMQ listeners (transport only):** `<Stage>Listener` — receives the message and
  delegates to the handler. Keep transport separate from logic.
- **Entities:** singular domain noun — `Job`, `Segment`, `User`.
- **Repositories:** `<Entity>Repository`.
- **DTOs:** suffix by purpose — `UploadRequest`, `UploadResponse`, `JobStatusResponse`.
  Never reuse an entity as a DTO.
- **Queue messages:** `<Stage>Task` as `record`s — `PrepareTask`, `TranscodeTask`, `PackageTask`.
- **Config:** `<Area>Properties` with `@ConfigurationProperties` — `PipelineProperties`,
  `StorageProperties`, `RabbitProperties`.
- **Enums:** singular type, `UPPER_SNAKE` constants — `JobStatus.AWAITING_UPLOAD`.
- **Policies/strategies:** name the decision — `ErrorClassifier`, `LadderPolicy`, `RetryPolicy`.
- **Banned suffixes:** `Manager`, `Util`, `Helper`, `Processor` — role-less; they become
  dumping grounds. If you can't name a class by what it does, its responsibility is unclear.
- **`Service`:** avoid the generic `XService` in package-by-feature — prefer the specific
  role (`Handler`, port, policy). Reserve `Service` for genuine cross-feature domain logic.

**Methods**
- **Ports:** short capability verbs, no tech leak — `probe()`, `transcode()`, `split()`,
  `upload()`, `presignPut()`, `download()` (not `s3Download()`).
- **Repositories:** Spring Data derived grammar — `findByJobIdAndRung(...)`,
  `existsByJobIdAndStatusNot(...)`.
- **State transitions:** intent verbs, never setters — `markProcessing()`, `markDone()`,
  or one guarded `transitionTo(newStatus)`. Never `setStatus()`.
- **Booleans:** `is/has/can` — `isTerminal()`, `hasAllSegmentsDone()`, `canRetry()`.
- **Fan-in claim:** `tryClaimPackaging(jobId, rung) -> boolean` — `try` signals it may fail
  (the atomic race).
- **Controllers:** action names — `createUpload()`, `completeUpload()`, `getJobStatus()` —
  not HTTP-verb names like `postUpload()`.

**Constructors**
- **Constructor injection only**, `final` fields, one constructor (no `@Autowired` needed).
  Pick one style — Lombok `@RequiredArgsConstructor` OR hand-written — and stay consistent.
- **Entities:** creation via factory (`Job.create(userId, sourceKey)`), not no-arg + setters.
  Keep the JPA-required no-arg constructor `protected`.
- **DTOs/messages:** `record`s; add a compact constructor only to validate.
- **Static factories** when they add clarity — `MediaInfo.from(probeJson)`,
  `TranscodeTask.forSegment(jobId, segmentId, rung)` — over ambiguous overloaded constructors.

**Example (names make the architecture readable):**
```
transcode/  TranscodeListener  (receives TranscodeTask)  TranscodeHandler  (orchestrates)  TranscodeTask (record)
storage/    Transcoder (port)  FfmpegTranscoder (adapter)
```

## Design principles & patterns

Follow **SOLID**, with **Open/Closed** emphasized: code is *open for extension, closed for
modification*. Adding a capability (a new output format, rung, validation, or error class)
must mean **adding a class**, not editing existing, tested code. Depend on interfaces, not
concretions (DIP), and keep interfaces small and role-specific (ISP).

Apply the patterns below **only at these named seams** — they exist because real extension
is anticipated there. Do NOT wrap everything in patterns; unneeded indirection is a defect.

| Seam (real extension point) | Pattern | Rule |
|---|---|---|
| Output packaging: **MP4 now, HLS next** | **Strategy** | `Packager` interface; `Mp4Packager`, `HlsPackager`. Adding HLS must not touch MP4 code. This is the primary OCP test for the project. |
| Selecting the packager from `OUTPUT_MODE` | **Factory / Registry** | One factory resolves the `Packager`. Selection logic lives in one place; callers never `if (mode == ...)`. |
| External tools: FFmpeg, ffprobe, ClamAV, S3 | **Adapter / Ports** | Wrap each behind an interface (`Transcoder`, `MediaProbe`, `VirusScanner`, `BlobStore`). Enables MinIO-vs-S3 swap and mocking in tests. App code never calls a vendor SDK directly. |
| Prepare-stage validation (format → size/duration → ffprobe → scan) | **Chain of Responsibility** | Each check is a handler; add a new check by adding a link, not editing the chain. Order matters (cheap/definitive checks first). |
| Shared worker skeleton (download → work → update DB → ack, with retry classification) | **Template Method** | `AbstractStageWorker` owns the skeleton + error handling; subclasses implement only the stage-specific step. Prevents divergent reliability logic across stages. |
| Building FFmpeg command lines (many optional flags) | **Builder** | `FfmpegCommandBuilder` — no ad-hoc string concatenation of args. |
| Transient-vs-permanent error routing | **Strategy / policy** | An `ErrorClassifier` maps exceptions to `RETRY` or `DEAD_LETTER`. New error types extend the classifier, not the retry dispatcher. |
| Data access | **Repository** | Spring Data JPA repositories. Entities never leak past the service layer; controllers see DTOs. |
| Rung ladder derivation from source resolution | **Strategy** | `LadderPolicy` so "which rungs for this source" is swappable (fixed ladder now, quality-aware later) without editing prepare logic. |

Optional / avoid over-engineering:
- **State pattern** for job/segment transitions is usually overkill here — prefer explicit
  guarded transition methods (a single `transitionTo(...)` that validates legal moves)
  over a full state-object graph.
- **Transactional Outbox** is the production-grade answer to the dual-write problem
  (Golden rule 3). Not required for v1 — the commit-then-publish + reconciliation-sweep
  approach is acceptable — but structure the publish step so an outbox can replace it later
  without touching business logic.

**Litmus test before introducing a pattern:** can you name the *second* concrete
implementation it enables (e.g. HLS as the second `Packager`)? If not, use a plain class
and refactor to the pattern when the second case actually arrives (YAGNI).

## Database

- All schema changes go through **Flyway migrations** in `db/migrations/`. Never edit a
  shipped migration; add a new one.
- Foreign keys enforced (`jobs.user_id`, `segments.job_id`). `ON DELETE CASCADE` for segments.
- Idempotency guard: `UNIQUE (job_id, rung, segment_index)` on `segments`.
- Index what the pipeline queries: `segments(job_id, rung)`, `jobs(status)`.
- Fan-in and completion are conditional single-statement updates (see Golden rule 5).

## Messaging (RabbitMQ)

- One queue per stage + a shared `dead-letter.queue` + a `retry.delay.queue` (TTL backoff). The
  retry queue is fed by a **fanout `retry.exchange`**: a transient failure is re-published there with
  routing key = origin stage queue and a per-message TTL; the queue's default-exchange DLX (no fixed
  routing key) then dead-letters the expired message back to that origin stage via its retained key.
- **Retry policy**: max `RETRY_MAX_ATTEMPTS` (default 3), backoff `2s → 8s → 30s`.
  - **Transient** errors (S3 5xx, timeouts) → retry via delay queue.
  - **Permanent** errors (corrupt input, missing key, infected) → DLQ on first failure.
    Do not waste retries on errors that can't succeed.
- **`prefetch = 1`** per worker so backlogs spread evenly.
- Messages are small JSON records referencing S3 keys — never payload bytes.

## Configuration

- **Everything env-driven** (see `.env.example`). No magic numbers in code.
- Key knobs: `OUTPUT_MODE` (mp4|hls), `SEGMENT_TARGET_SECONDS`, `MAX_DURATION_SECONDS`,
  `MAX_SIZE_BYTES`, `RETRY_MAX_ATTEMPTS`, `RETRY_BACKOFF_SECONDS`, `PREFETCH`,
  `IN_FLIGHT_JOB_CAP`, `UPLOAD_DEADLINE_MINUTES`.
- Bind config with `@ConfigurationProperties`, not scattered `@Value`.

## Reliability

- **Reconciliation sweep** (scheduled): re-drive jobs stuck between a DB commit and a failed
  publish. Pairs with idempotent workers.
- **Timeout reaper** (scheduled): jobs past `upload_deadline` in `AWAITING_UPLOAD` →
  `EXPIRED`, and `AbortMultipartUpload`. (S3 has no failed-upload event; detect via absence.)
- **Admission control**: over `IN_FLIGHT_JOB_CAP`, reject `/uploads` with `429`.
- Clean up local scratch files immediately after upload; intermediate S3 segments after
  packaging consumes them.

## Operations & observability

- **Startup ordering.** Workers must not boot before RabbitMQ/Postgres are ready. Use
  `depends_on` with **healthchecks** in `docker-compose.yml`, and connection-retry on the
  app side (Spring's `spring.rabbitmq`/datasource retry). Assume dependencies come up late.
- **Graceful shutdown.** On SIGTERM (scale-down / redeploy) a worker must finish its current
  segment or cleanly nack/requeue it — never silently drop in-flight work. Enable Spring
  graceful shutdown; stop the RabbitMQ listener container before the DB/S3 clients close.
  This is part of the idempotency/reliability story (Golden rule 4).
- **Metrics.** Spring Boot Actuator + Micrometer expose queue depth, jobs/min, per-stage
  latency, and success/failure counts. This doubles as how the scaling benchmark is measured.
  Optional Prometheus + Grafana for a dashboard ("watch the queue drain as workers scale").
- **API docs.** `springdoc-openapi` auto-generates Swagger UI from the controllers — keep
  endpoints self-documenting; don't hand-maintain API docs.
- **CI.** `.github/workflows/ci.yml` runs `./mvnw verify` (unit + Testcontainers) on every PR.
  A red build blocks merge — this enforces the Definition of done, not honor system.
- **Final output keys are deterministic, not stored.** Per-rung outputs and manifests use
  derived keys — `{job_id}/{rung}.mp4`, `{job_id}/hls/{rung}.m3u8`, `{job_id}/master.m3u8`.
  The DB stores per-*segment* keys; final artifacts are computed from `job_id` + rung, so
  there's nothing extra to persist and a re-run overwrites cleanly.

## Testing

- Unit-test pure logic (limit checks, ladder derivation, state transitions) without I/O.
- **Testcontainers** for Postgres and RabbitMQ integration tests. Do not mock the broker/DB
  for pipeline tests.
- Explicitly test: the **race** (concurrent fan-in claims → exactly one packaging trigger),
  redelivery/idempotency, permanent-vs-transient error routing, oversized-input rejection.
- Use tiny fixture clips; never commit test videos (git-ignored).

## Security

- Least-privilege IAM user, one bucket, only the actions used (`GetObject`, `PutObject`,
  multipart, presign). Never root keys.
- Run FFmpeg workers locked down: non-root, resource limits, no unnecessary network. Treat
  every input as hostile (decoder-exploit surface).
- S3 lifecycle rules: abort incomplete multipart uploads; expire intermediates.

## Git

- **Never commit to `main`** — feature branch + PR only (see Working agreement).
- `.gitignore` guards `.env`, build output, IDE files, and media. Never commit secrets.
- Small, focused commits; imperative messages ("Add prepare-stage ClamAV scan").
- Update `DEVLOG.md` and affected docs in the same PR as the change.
- Never commit generated `target/`, `.env`, or test media.

---

## Build & run

```bash
docker compose up --build            # full stack: api, workers, rabbitmq, postgres, clamav
docker compose up --scale transcode-worker=5   # scale the transcode tier (scaling demo)
./mvnw test                          # unit + Testcontainers integration tests
./mvnw spring-boot:run               # run one component locally (set profile)
```

Flyway migrations run on startup. Copy `.env.example` → `.env` and fill values first.

## Scope guards (do NOT build unless asked)

- No auth beyond a minimal `users` table (no OAuth/roles/permissions).
- No Kubernetes — `docker-compose` only. (K8s is a documented post-completion stretch.)
- No fancy frontend — a minimal `hls.js` page to prove playback is enough.
- No streaming/live ingestion — VOD only.
- No extra tables (audit, soft-delete, separate `outputs`) — add only when a real query needs one.

## Build order (current)

1. compose skeleton → 2. migrations + entities → 3. upload flow (`/uploads`, `/complete`)
→ 4. prepare worker → 5. transcode worker + atomic fan-in → 6. packaging (MP4 then HLS)
→ 7. status polling (SSE later) → 8. reliability (retries, DLQ, sweeps) → 9. scaling demo
→ 10. README polish.

**Start with a vertical slice**: one video, one rung, MP4, end-to-end — before adding the
ladder, HLS, or reliability machinery.
