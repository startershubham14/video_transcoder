# Scaling benchmark

Proves the core claim of the project: **transcode throughput scales horizontally with the
number of workers.** Run this once the pipeline works end-to-end (MP4, full ladder), then
paste the numbers + chart into the top-level README "Results" section.

## Method

1. Pick a fixed **sample set**: the same K source videos (same resolution/duration) for
   every run, so runs are comparable. Keep them small (local test clips, git-ignored) — e.g. a
   `samples/` dir, or one clip submitted K times via `--copies K`.
2. For each worker count **W ∈ {1, 2, 4}** (bounded by your CPU cores):
   - `docker compose up -d --scale transcode-worker=W` (wait for `clamav` healthy on first boot).
   - Run the driver (`scripts/bench.py`, below) — it submits all K jobs at once and times the drain.
   - Optionally watch it live in Grafana (`http://localhost:3000`): `pipeline_queue_depth{queue="transcode.queue"}`
     spikes then drains faster at higher W; `rate(pipeline_segments{status="DONE"}[1m])` rises.
3. `bench.py` records wall-clock (first submission → last job terminal) and throughput = `K / minutes`.
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

## Runner — `scripts/bench.py`

Submits K jobs (the thin upload handshake, reusing the same flow as `smoke.py`), starts the clock at
the first submission, then polls `GET /jobs/{id}` until every job is terminal
(`COMPLETED`/`FAILED`/`EXPIRED`). Prints wall-clock, throughput (jobs/min), any failures, and a
ready-to-paste results-table row. Stdlib only — no dependencies.

```bash
# one clip submitted 8 times, against the default API, labelled as the 2-worker run
python scripts/bench.py samples/clip.mp4 --copies 8 --label 2

# or a fixed sample set
python scripts/bench.py samples/*.mp4 --label 4
```

Flags: `--api` (default `http://localhost:8080`), `--copies N`, `--label W` (worker count for the row),
`--poll` (seconds between status polls, default 3), `--timeout` (default 1800s). Run it once per worker
count, bringing the tier up with `docker compose up -d --scale transcode-worker=W` between runs, and
paste each printed row into the Results table above.
