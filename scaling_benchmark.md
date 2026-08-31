# Scaling benchmark

Proves the core claim of the project: **transcode throughput scales horizontally with the
number of workers.** Run this once the pipeline works end-to-end (MP4, full ladder), then
paste the numbers + chart into the top-level README "Results" section.

## Method

1. Pick a fixed **sample set**: the same K source videos (same resolution/duration) for
   every run, so runs are comparable. Keep them small (local test clips, git-ignored).
2. For each worker count **W ∈ {1, 2, 4}** (bounded by your CPU cores):
   - `docker compose up --scale transcode-worker=W`
   - Submit all K jobs at once (script below).
   - Measure **wall-clock time** from first submission to the last job reaching `COMPLETED`
     (poll `GET /jobs/{id}` or read the DB).
3. Record throughput = `K / total_minutes` (jobs/min), and optionally segments/min.
4. Repeat each run 3× and take the median to reduce noise.

Keep everything else constant across runs: same `SEGMENT_TARGET_SECONDS`, same ladder,
same machine, no other heavy load.

## What to look for

- Throughput should rise with W, roughly linearly, **until FFmpeg saturates your cores** —
  then it plateaus. Showing *and explaining* the plateau (CPU-bound work, ~1 worker/core)
  is a stronger result than pretending it scales forever.
- Note the point where the queue stops draining faster: that's your practical worker ceiling
  on this machine.

## Results (fill in after running)

| Workers | Median wall-clock (min) | Throughput (jobs/min) | Speedup vs 1 |
|--------:|------------------------:|----------------------:|-------------:|
| 1       | —                       | —                     | 1.00×        |
| 2       | —                       | —                     | —            |
| 4       | —                       | —                     | —            |

_Chart: plot workers (x) vs throughput (y); commit the image and embed it in the README._

## Runner (stub)

`run.sh` — submit K jobs and time the drain. Implement once the API's upload/complete
endpoints exist. Pseudocode:

```
for each sample video:
    POST /uploads -> get presigned URLs + job_id
    upload parts to S3
    POST /jobs/{id}/complete
start_timer
poll all job_ids until every status == COMPLETED (or FAILED)
stop_timer -> report total time + throughput
```
