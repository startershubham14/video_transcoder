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
