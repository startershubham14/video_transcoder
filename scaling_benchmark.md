# Scaling benchmark

Proves the core claim of the project: **transcode throughput scales horizontally with the
number of workers.** Run this once the pipeline works end-to-end (MP4, full ladder), then
paste the numbers + chart into the top-level README "Results" section.

## Method

1. Pick a fixed **sample set**: the same K source videos (same resolution/duration) for
   every run, so runs are comparable. Keep them small (local test clips, git-ignored) — e.g. a
   `samples/` dir, or one clip submitted K times via `--copies K`.
2. For each worker count **W ∈ {1, 2, 4}** (bounded by your CPU cores), applying the CPU-cap overlay
   [`docker-compose.bench.yml`](docker-compose.bench.yml) so each worker gets a fixed 2-core quota
   (otherwise one unbounded FFmpeg spreads across most cores and adding workers just oversubscribes):
   - `docker compose -f docker-compose.yml -f docker-compose.bench.yml up -d --scale transcode-worker=W --no-recreate`
     (wait for `clamav` ready on first boot).
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

## Results

Run (2026-09-08): **2 jobs** of a 24s 1920×1080 high-detail clip (→ 720p/480p/360p — 3 rungs × 3
segments of real FFmpeg work per job; upload ~1s, so transcode-bound), MP4 output, **median of 3 runs**,
0 failures at every worker count. Each transcode worker capped at **2 CPUs** via
`docker-compose.bench.yml` on a 12-core laptop, so the pool is measurable on one host (1→2→4 workers ≈
2→4→8 cores, all under the 12-core ceiling).

| Workers | Wall-clock (min) | Throughput (jobs/min) | Speedup vs 1 |
|--------:|-----------------:|----------------------:|-------------:|
| 1       | 2.19             | 0.91                  | 1.00×        |
| 2       | 1.41             | 1.41                  | 1.55×        |
| 4       | 1.14             | 1.75                  | 1.92×        |

**Reading it:** throughput rises monotonically — **1.55× at 2 workers, 1.92× at 4** — sub-linearly, as
expected. Only the transcode stage parallelizes; the per-job prepare (probe/scan/split) and per-rung
concat/package stages are serial, so by Amdahl the curve stays under the ideal line, and a small 2-job
batch under-fills 8 cores at W=4 (more jobs would push the high end closer to linear). The earlier
attempt — a 12s 720p clip, single runs, uncapped workers — was overhead-bound and oversubscribed, and
*regressed* at 4; capping per-worker CPU + a transcode-bound clip + medians is what makes the curve
clean. Watch `pipeline_queue_depth` in Grafana during a run to see the transcode queue drain faster as
W rises.

_Chart: `bench.py` appends each run to `scripts/bench_results.csv`; `python scripts/plot.py` renders
`docs/img/scaling.svg` (workers vs throughput, with the ideal-linear reference), which the README
[Results](README.md#results--horizontal-scaling) section embeds. The chart currently reflects the
preliminary numbers above — rerun rigorously and regenerate to replace it._

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

Flags: `--api` (default `http://localhost:8080`), `--copies N`, `--label W` (worker count — also the
CSV key), `--runs N` (repeat the submit/drain cycle and report the **median** wall-clock, default 1),
`--poll` (seconds between status polls, default 3), `--timeout` (default 1800s), `--results PATH`
(CSV to append to, default `scripts/bench_results.csv`), `--no-write` (skip the CSV). Run it once per
worker count, bringing the tier up with `docker compose up -d --scale transcode-worker=W` between runs;
each run appends a row to the CSV. Then regenerate the chart:

```bash
python scripts/plot.py     # scripts/bench_results.csv → docs/img/scaling.svg
```

Also paste each printed row into the Results table above for the human-readable record.
