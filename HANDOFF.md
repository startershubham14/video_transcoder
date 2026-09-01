# HANDOFF — Distributed Video Transcoding Pipeline

Session handoff doc. The pipeline **works end-to-end**; this captures state, how to run it, and
what's left so a new session can continue on `dev` without prior context.

## Current state (as of tip)
- Branch to work on: **`dev`** (published to `origin/dev`). `main` lags behind — promote via a
  **merge-commit** PR (`main ← dev`); **never merge `main` → `dev`** (it has mis-reverted work 3×).
- **Build-order steps 1–6 are DONE and verified end-to-end in Docker** with real videos
  (small clip, 147 MB trailer, larger file → all reached `COMPLETED` with playable MP4s):
  1. compose skeleton · 2. migrations + entities · 3. upload flow (`/uploads`,`/complete`) ·
  4. prepare worker (probe/scan/limits/keyframe-split/fan-out) · 5. transcode worker + **atomic
  per-rung fan-in** · 6. packaging (**MP4 milestone**).
- `./mvnw -B verify` is green (33 tests). CI (`.github/workflows/ci.yml`) runs `./mvnw verify`
  on PRs to `main`.

## ⚠️ Local-only files the new session needs
`CLAUDE.md` (conventions — read it first) and `DEVLOG.md` (full history + backlog) are **git-ignored
/ local-only** by the owner's choice. A fresh clone/other machine WON'T have them. If not present,
copy them from the previous working dir. On the same machine/folder they're already on disk.

## How to build / test
Toolchain is user-local (not on system PATH by default):
```
JAVA_HOME=/c/Users/Shubham/devtools/jdk-21.0.12.1+1 ./mvnw -B verify
```
Maven 3.9.9 + Temurin JDK 21 live in `C:\Users\Shubham\devtools`. A local Postgres 18 runs on
`localhost:5432` (user `dbuser123`, password given per-session, NOT stored) — used for schema/boot
checks via throwaway DBs. Docker daemon is NOT reachable from the agent shell, so `docker compose`
is run by the human.

## How to run it end-to-end (human runs Docker)
```
docker compose up -d --build          # api :8080, rabbitmq :15672 (guest/guest), minio :9001 (minioadmin)
# wait for clamav (healthy); first start downloads virus defs (~1-2 min)
python scripts/smoke.py <video.mp4>   # POST /uploads -> PUT presigned parts -> POST /complete
```
Watch: `docker compose logs -f transcode-worker`; RabbitMQ UI (queues drain, dead-letter.queue empty);
MinIO console (`<jobId>/source.mp4`, `segments/*.ts`, `<rung>/<i>.ts`, then finals `<jobId>/<rung>.mp4`);
Postgres `SELECT status FROM jobs...`. Job → `COMPLETED` when all rungs packaged.
- `API_PORT=8081 docker compose up` remaps the api host port (8080 conflicts).
- Presigned URLs are host-reachable via `AWS_S3_PUBLIC_ENDPOINT=http://localhost:9000` (compose default).

## Architecture quick map (package-by-stage under `dev.shubham.transcoder`)
- Ports/adapters: `storage.BlobStore`←`S3BlobStore`; `prepare.{MediaProbe,VirusScanner,Splitter}`←
  `Ffprobe/ClamAv/Ffmpeg…`; `transcode.Transcoder`←`FfmpegTranscoder`. `media.ProcessRunner` +
  `FfmpegCommandBuilder` wrap all external processes.
- Each stage = `<Stage>Handler` (logic) + `<Stage>Listener` (transport, extends
  `messaging.AbstractStageWorker`, manual ack → nack/DLQ on failure). Profiles: `api` / `worker`
  (prepare+package) / `transcode`.
- **Atomic fan-in:** `SegmentRepository.tryClaimPackaging(jobId,rung)` (native guarded UPDATE) — one
  worker per rung wins → publishes `PackageTask`. **Job completion:** `JobRepository.tryComplete`
  (guarded CONCATENATING→COMPLETED) once every rung's output exists.
- Packaging strategy: `Packager` (`mode`,`outputKey`,`packageRung`,`finalizeJob`) resolved by
  `PackagerFactory` from `OUTPUT_MODE`. `Mp4Packager` done; `HlsPackager` is a stub.
- Status enums stored as **varchar** (`@Enumerated(STRING)`), NOT native PG enums (V2 migration) —
  native enums broke Hibernate bulk-update casts. Ids are DB-generated (`@Generated INSERT`).

## What's left (backlog — details in DEVLOG.md)
- **Status endpoint** (step 7): `JobStatusService.getStatus` — progress from segment rows + presigned
  download URLs (`JobController.getJobStatus` already wired to it; currently a stub → 500 if called).
- **HLS**: implement `HlsPackager` (2nd Packager) + `finalizeJob` master playlist + `hls.js` page.
- **Reliability**: transient retry-delay/backoff via `ErrorClassifier` (AbstractStageWorker TODO —
  today all failures DLQ on first try); `ReconciliationSweep` + `UploadTimeoutReaper` bodies (currently
  no-op); global `@RestControllerAdvice` for consistent API error bodies.
- **Observability** (requested): Actuator + Micrometer + Prometheus + Grafana dashboard (services
  health, queue depth, throughput); structured logging with MDC (jobId/segmentId/rung).
- **Testcontainers** integration tests (fan-in race, idempotency, error routing — placeholders
  `@Disabled` today); **scaling benchmark** (`scaling_benchmark.md`); README results/diagrams.

## Gotchas already hit & fixed (don't reintroduce)
- ClamAV default StreamMaxLength 25 MB → mounted `docker/clamav/clamd.conf` raising it to ~2 GB.
- AdmissionControlInterceptor/AdmissionPolicy were stubs that 500'd every upload — now implemented.
- `*.conf` and `mvnw` pinned to LF in `.gitattributes` (CRLF breaks container configs / the wrapper).
- Coordinate pushes: only one session pushes `dev` at a time (non-fast-forward otherwise).
