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
