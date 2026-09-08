# Media assets for the README

- **`scaling.svg`** — the scaling-benchmark chart. Generated, not hand-drawn:
  ```bash
  python scripts/plot.py     # reads scripts/bench_results.csv → writes docs/img/scaling.svg
  ```
  Re-run `scripts/bench.py` (which appends to the CSV) then `plot.py` to refresh it. Committed so
  the README renders it without a build step.

- **`demo.gif`** — the 20–30s walkthrough (not committed yet). Once recorded, drop it here and
  uncomment the embed line in the top-level `README.md` (`## Demo`).

## Recording the demo GIF

Show the pipeline end-to-end, ideally in one take:

1. **Upload + live progress.** In a terminal, submit a clip and stream its status:
   ```bash
   python scripts/smoke.py samples/720p.mp4        # prints the job id
   curl -N http://localhost:8080/jobs/<id>/events  # live SSE: progress → COMPLETED + URLs
   ```
2. **Scale + queue drain.** With Grafana open (`http://localhost:3000`, the pipeline dashboard),
   submit a batch and scale the transcode tier — watch `pipeline_queue_depth{queue="transcode.queue"}`
   spike then drain faster:
   ```bash
   docker compose up -d --scale transcode-worker=5
   python scripts/bench.py samples/720p.mp4 --copies 12 --label 5
   ```
3. **HLS playback.** With `OUTPUT_MODE=hls`, open the player on a completed job:
   `http://localhost:8080/player.html?src=<master .m3u8 url>`.

### Capture tips

- Any screen recorder works; export to GIF (e.g. with ffmpeg to keep it small):
  ```bash
  # trim + downscale a screen recording to a lightweight looping GIF
  ffmpeg -i recording.mp4 -vf "fps=12,scale=960:-1:flags=lanczos" -loop 0 docs/img/demo.gif
  ```
- Keep it under ~10 MB so it loads fast on GitHub. 12 fps and ≤960px wide is plenty.
- Split-screen the terminal (SSE progress) and the Grafana dashboard so both stories show at once.
