# Video Transcoding Pipeline — Design Notes

A resume project: a distributed video-processing server that takes an input video
(e.g. 1080p) and produces multiple lower-resolution / adaptive-streaming versions.
The interesting engineering is **not** the transcoding itself (FFmpeg does that in
one command) — it's the distributed job-processing system built *around* it.

**Timeline target:** 15–30 days.
**Lean:** distributed systems (queues, workers, parallel processing, scaling).
**Language (planned):** Python (FastAPI + Celery + Redis). Node.js (Express + BullMQ) is an equally valid alternative.

---

## 1. Why this is a good resume project

- The naive version ("upload → run FFmpeg → return files") is a weekend script and
  an interviewer sees through it fast. Transcoding is a solved problem you shell out to.
- The strong version is a **distributed job-processing system that happens to do video**:
  async job queue, worker pool, object storage, progress tracking, failure handling,
  and adaptive-bitrate (HLS) output.
- Framing it as a distributed system (not a "video app") is what makes it stand out.

### The "senior" differentiators (optional, time-permitting)
- **Parallel chunked transcoding** — split video into segments, transcode them
  concurrently across workers, then concatenate. Strongest single differentiator.
- Horizontal scaling (multiple worker containers sharing a queue).
- Basic observability (queue depth, throughput, avg transcode time).

### Scoping cautions
- Video encoding has a long tail of edge cases (codecs, containers, audio sync,
  variable frame rates). **Constrain the input** (e.g. MP4/MOV with H.264) and say so.
  Good scoping is itself a signal of engineering judgment.
- This project is backend/systems-flavored, not ML. An optional ML angle (per-title /
  per-scene quality-aware encoding, scene-change detection) exists but should stay a
  stretch goal, not the core.

---

## 2. Infrastructure decisions

### Cloudflare Workers — evaluated and rejected for the compute layer
- Workers run in **V8 isolates**, not containers: **cannot run native binaries**, so
  FFmpeg is impossible there.
- Free tier: 100k requests/day, **10 ms CPU time/request**, 128 MB memory. Even paid
  tops out at 5 min CPU/request. Transcoding needs minutes of solid CPU — architecturally
  a non-fit.
- **Cloudflare Containers** *can* run Docker images (FFmpeg works), but require the
  **Workers Paid plan ($5/month)** — no truly free container tier.

### Chosen direction
- **Build & learn locally with `docker-compose`** — unthrottled, free, and where the
  real distributed-systems behavior is visible (kill a worker, scale workers, watch the
  queue redistribute). Don't let free-tier limits shape the architecture.
- **Storage: AWS S3** (decided). Use **MinIO** locally during development — it's
  S3-compatible, so the same `boto3` calls work against both; nothing changes on deploy.
- **Deploy options for a live demo:** Oracle Cloud always-free VM ($0), or Cloudflare
  Containers + R2 + Queues ($5/mo, clean integration, R2 has no egress fees).
- **README tip:** describe the architecture cloud-agnostically ("workers pull from a
  queue, transcode, write to object storage") — reads as more mature than naming one vendor.

---

## 3. What to learn (grouped, in build order)

1. **FFmpeg (drive it, don't build it)** — transcode command structure
   (`-vf scale`, `-c:v libx264`, bitrate/CRF); separate MP4s vs **HLS** (`.ts` segments +
   `.m3u8` manifest); parsing progress from stderr. ~1–2 days.
2. **Message queues & async jobs (the heart)** — producer/consumer pattern; Redis as
   broker + Celery/RQ; broker vs task vs result backend. API enqueues and returns a job
   ID immediately; workers do the slow work. **Invest most here.**
3. **Object storage & the S3 API** — `put_object`/`get_object`, **presigned URLs**,
   MinIO locally. Files live in storage; workers pull from and write back to it.
4. **Web API framework** — FastAPI. Endpoints: `POST /uploads`, `GET /jobs/{id}`,
   list results. Progress via **polling** first (WebSocket/SSE later).
5. **Docker & docker-compose** — containerize API, worker (with FFmpeg installed),
   Redis/MinIO; run all together; run **multiple worker containers** to show scaling.
6. **Distributed-systems concepts (makes it "senior")** — idempotency &
   at-least-once delivery; retries & dead-letter queues; failure handling & cleanup of
   partial output; worker concurrency (~1 worker per CPU core, and why).
7. **Parallel chunked transcoding (differentiator, optional)** — split → transcode
   segments concurrently → concatenate; handle a failed chunk; reassemble in order.
8. **Observability (light, optional)** — queue depth, jobs processed, avg transcode
   time; JSON endpoint or tiny dashboard.

**Suggested learning order:** FFmpeg command → FastAPI endpoint → Redis + Celery worker →
MinIO storage → Dockerize → retries + multiple workers → chunked parallelism.

**Timeline mapping:** items 1–5 + basics of 6 = must-have core (~weeks 1–2, ship-able on
its own). Items 7 and the deeper parts of 6 and 8 = weeks 3–4 stretch.

---

## 4. Input design

### The input is two layers
- **The video file** — the large raw bytes to transcode.
- **The job description** — a tiny JSON message ("transcode *this* file into *these*
  outputs"). **This** is what travels through the API and the queue — never the bytes.

Keep them separate: the queue, API responses, and status records deal only with the small
thing and *reference* the big thing in storage.

### How the file gets in — use presigned/multipart, not through the API
- **Rejected (Approach A):** `POST /upload` with `multipart/form-data` through the API —
  every byte flows through the API server. Fine for a toy, wrong for a system.
- **Chosen:** client uploads **directly to S3** via presigned URLs (S3 **multipart
  upload** for large files — resumable, parallel parts, reliable). Bytes never touch the
  API server. API stays lightweight.

### Request contract (metadata only)
```
POST /uploads
{ "filename": "my_video.mp4", "size_bytes": 734003200, "content_type": "video/mp4" }

-> { "job_id": "a1b2c3",
     "upload_url": ".../a1b2c3/source.mp4?X-Amz-Signature=...",
     "expires_in": 3600 }
```

### Never trust the input — probe it
- After the file lands in S3, run **`ffprobe`** before transcoding:
  `ffprobe -v quiet -print_format json -show_format -show_streams source.mp4`
- Gives real width/height/duration/codec/bitrate. Use it to:
  - **Reject invalid/corrupt video** (if ffprobe fails, fail the job — don't hand garbage
    to FFmpeg).
  - **Derive a sensible output ladder** from the *real* resolution (no pointless upscaling):
    1080p source → {720p, 480p, 360p}; 480p source → {360p}.

### Job payload on the queue (small, all references, no bytes)
```
{ "job_id": "a1b2c3",
  "source_key": "a1b2c3/source.mp4",
  "source": { "width": 1920, "height": 1080, "duration": 154.2, "codec": "h264" },
  "outputs": [ { "height": 720, "bitrate": "2500k" },
               { "height": 480, "bitrate": "1000k" },
               { "height": 360, "bitrate": "600k" } ],
  "status": "queued" }
```

### Constraints to define up front
- Accepted formats (e.g. MP4/MOV + H.264) — stated explicitly.
- Max file size / duration ceiling (so one upload can't monopolize workers).
- Output ladder: auto-derived from source (simpler, chosen for v1) vs client-specified.

---

## 5. Critical distinction — S3 "parts" vs video "segments"

The word "chunk" means two different things. **This is the key insight to get right.**

- **S3 multipart parts** are a *transfer-layer* concept: arbitrary byte ranges (≥5 MB,
  up to 10,000 parts) reassembled on `CompleteMultipartUpload`. A part boundary can fall
  mid-frame / mid-GOP. **An S3 part is not a valid standalone video** — you cannot hand it
  to FFmpeg.
- **Video segments** are a *content-layer* concept: to transcode independently, a segment
  must be **independently decodable**, i.e. start on a **keyframe (GOP boundary)**.

These boundaries are unrelated. Therefore you **cannot transcode S3 parts as they arrive**,
and the "process while uploading" idea doesn't work at this layer.

Also: **S3 has no per-part event.** Event notifications fire on
`s3:ObjectCreated:CompleteMultipartUpload` (the whole object). The natural trigger is
**upload completion**, for the whole file.

### Correct VOD sequence (upload and transcode are sequential; parallelism is *after* upload)
1. **Upload** — client does S3 multipart upload (parallel parts, direct to S3 via
   presigned URLs).
2. **Complete** — backend calls `CompleteMultipartUpload` (preferred: validates parts,
   gives a clean in-process trigger) — or listen for the S3 completion event.
3. **Probe** — `ffprobe` for real dimensions/duration/codec.
4. **Split** — split source into **keyframe-aligned segments** (FFmpeg segment muxer with
   `-c copy` splits at existing keyframes cheaply, no re-encode). **These are the real
   processing chunks.**
5. **Enqueue** — one message per segment: `{job_id, segment_index, segment_key, output_rung}`.
6. **Transcode in parallel** — N workers pull segments and transcode concurrently.
   **This is where the distributed parallelism actually happens.**
7. **Concatenate** — once all segments for a rung are done, stitch them (FFmpeg concat
   demuxer) into the output MP4, or assemble the HLS playlist.

So the earlier instinct — *chunk-processing requests on the queue* — is correct; the chunks
are the **keyframe-aligned segments from step 4**, not S3 multipart parts from step 1.

### Overlapping upload + processing (the "meanwhile")
- A real pattern, but it's **live/streaming ingestion** (client sends keyframe-aligned
  segments, server transcodes each as it arrives) — substantially more complex.
- For a 15–30 day VOD project: use the sequential phases above; mention streaming
  ingestion in the README as a possible extension.

---

## 6. Open decisions (to resolve next)

- **Output ladder:** auto-derive from source (leaning yes for v1) vs client-chosen.
- **Who calls `CompleteMultipartUpload`:** backend (preferred — validation + clean
  trigger, no eventual-consistency surprises) vs S3 event notifications.
- **Segment length:** e.g. ~6–10 s. Trade-off: more/smaller segments = more parallelism
  but more concat overhead and more queue messages.

## 7. Suggested next steps
- Design the multipart upload flow (API endpoints + presigned-URL-per-part handshake), **or**
- Design the split-and-enqueue step (where parallel transcoding begins), **or**
- Sketch the `docker-compose` architecture + repo structure to see how the pieces sit together.
