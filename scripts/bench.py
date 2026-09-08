#!/usr/bin/env python3
"""Scaling-benchmark driver: submit K jobs, time the drain to COMPLETED (stdlib only).

Proves the core claim — transcode throughput scales with the number of workers. Run it once per
worker count; it appends a machine-readable row to scripts/bench_results.csv (which scripts/plot.py
turns into docs/img/scaling.svg) and prints a ready-to-paste scaling_benchmark.md row.

  # 1 worker, median of 3 runs
  docker compose up -d --scale transcode-worker=1
  python scripts/bench.py samples/*.mp4 --label 1 --runs 3

  # 2 workers (bring the tier up, then re-run the same sample set)
  docker compose up -d --scale transcode-worker=2
  python scripts/bench.py samples/*.mp4 --label 2 --runs 3

  python scripts/plot.py     # regenerate the chart from the CSV

The clock starts at the first submission and stops when every job reaches a terminal state
(COMPLETED / FAILED / EXPIRED), polling GET /jobs/{id}. With --runs N the whole submit→drain cycle
repeats N times and the reported wall-clock is the median (reduces warm-up/measurement noise, as the
method calls for). Keep everything else constant across runs (same sample set,
SEGMENT_TARGET_SECONDS, ladder, machine). Watch the queue drain live in Grafana
(http://localhost:3000) while it runs.

Usage:
  python scripts/bench.py <video>... [--api URL] [--copies N] [--label W] [--runs N]
                                     [--poll SECONDS] [--timeout SECONDS] [--results PATH]
  # --api defaults to http://localhost:8080 ; --copies submits each file N times (default 1)
"""
import argparse
import csv
import json
import math
import statistics
import time
import urllib.error
import urllib.request
from pathlib import Path

TERMINAL = {"COMPLETED", "FAILED", "EXPIRED"}


def _request(method, url, *, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        raise SystemExit(f"{method} {url} -> HTTP {e.code}\n{body}")
    except urllib.error.URLError as e:
        raise SystemExit(f"{method} {url} failed: {e.reason}")


def submit(api, path):
    """Run the thin upload handshake for one file; return its jobId. Mirrors smoke.py."""
    data = path.read_bytes()
    size = len(data)
    body = json.dumps({"filename": path.name, "sizeBytes": size, "contentType": "video/mp4"}).encode()
    _, raw, _ = _request("POST", f"{api}/uploads", data=body,
                         headers={"Content-Type": "application/json"})
    created = json.loads(raw)
    job_id, upload_id, part_urls = created["jobId"], created["uploadId"], created["partUrls"]

    n = len(part_urls)
    chunk = math.ceil(size / n) if n else size
    etags = []
    for i, url in enumerate(part_urls):
        part = data[i * chunk:(i + 1) * chunk]
        _, _, resp_headers = _request("PUT", url, data=part,
                                      headers={"Content-Type": "application/octet-stream"})
        etag = resp_headers.get("ETag") or resp_headers.get("Etag")
        if not etag:
            raise SystemExit(f"{path.name} part {i + 1}: no ETag in {resp_headers}")
        etags.append(etag)

    complete = json.dumps({"uploadId": upload_id, "partETags": etags}).encode()
    _request("POST", f"{api}/jobs/{job_id}/complete", data=complete,
             headers={"Content-Type": "application/json"})
    return job_id


def status_of(api, job_id):
    _, raw, _ = _request("GET", f"{api}/jobs/{job_id}")
    return json.loads(raw)["status"]


def drain(api, files, poll, timeout):
    """Submit every file, then poll until all jobs are terminal. Returns (elapsed_seconds, failures)."""
    total = len(files)
    start = time.monotonic()
    pending = set()
    for v in files:
        pending.add(submit(api, v))
    print(f"  submitted {total} job(s) in {time.monotonic() - start:.1f}s; draining ...")

    failed = []
    while pending:
        if time.monotonic() - start > timeout:
            raise SystemExit(f"timeout after {timeout:.0f}s; {len(pending)} job(s) still running")
        time.sleep(poll)
        for job_id in list(pending):
            state = status_of(api, job_id)
            if state in TERMINAL:
                pending.discard(job_id)
                if state != "COMPLETED":
                    failed.append((job_id, state))
        done = total - len(pending)
        print(f"    {done}/{total} terminal ({time.monotonic() - start:.0f}s)", end="\r", flush=True)
    print()
    return time.monotonic() - start, failed


def append_result(results_path, workers, wallclock_min, throughput):
    """Append one row to the machine-readable CSV that plot.py reads (writing a header if new)."""
    new = not results_path.exists()
    results_path.parent.mkdir(parents=True, exist_ok=True)
    with results_path.open("a", newline="") as fh:
        w = csv.writer(fh)
        if new:
            w.writerow(["workers", "wallclock_min", "throughput_jobs_min"])
        w.writerow([workers, f"{wallclock_min:.3f}", f"{throughput:.3f}"])


def main():
    default_results = Path(__file__).resolve().parent / "bench_results.csv"
    ap = argparse.ArgumentParser(description="Scaling benchmark: submit K jobs, time the drain.")
    ap.add_argument("videos", nargs="+", type=Path, help="source video file(s)")
    ap.add_argument("--api", default="http://localhost:8080", help="API base URL")
    ap.add_argument("--copies", type=int, default=1, help="submit each file this many times")
    ap.add_argument("--label", default="?", help="worker count, for the results row")
    ap.add_argument("--runs", type=int, default=1, help="repeat the submit/drain cycle N times, report the median")
    ap.add_argument("--poll", type=float, default=3.0, help="seconds between status polls")
    ap.add_argument("--timeout", type=float, default=1800.0, help="give up after this many seconds")
    ap.add_argument("--results", type=Path, default=default_results,
                    help="machine-readable CSV to append to (read by plot.py)")
    ap.add_argument("--no-write", action="store_true", help="don't append to the results CSV")
    args = ap.parse_args()
    api = args.api.rstrip("/")

    files = [v for v in args.videos for _ in range(args.copies)]
    for v in files:
        if not v.is_file():
            raise SystemExit(f"not a file: {v}")
    total = len(files)
    print(f"benchmark: {total} job(s) x {args.runs} run(s) to {api} (workers={args.label})")

    elapseds, all_failed = [], 0
    for run in range(1, args.runs + 1):
        print(f"run {run}/{args.runs}:")
        elapsed, failed = drain(api, files, args.poll, args.timeout)
        elapseds.append(elapsed)
        all_failed += len(failed)
        print(f"  run {run}: {elapsed:.1f}s ({elapsed/60:.2f} min); failed={len(failed)}")
        for job_id, state in failed:
            print(f"    FAILED: {job_id} -> {state}")

    median_elapsed = statistics.median(elapseds)
    minutes = median_elapsed / 60
    throughput = total / minutes if minutes else float("inf")
    print()
    runs_note = f" (median of {args.runs}: {[f'{e/60:.2f}' for e in elapseds]} min)" if args.runs > 1 else ""
    print(f"done: {total} job(s), workers={args.label}, {minutes:.2f} min{runs_note}; "
          f"throughput={throughput:.2f} jobs/min; failures={all_failed}")

    try:
        workers = int(args.label)
    except ValueError:
        workers = None
    if not args.no_write and workers is not None:
        append_result(args.results, workers, minutes, throughput)
        print(f"appended to {args.results} — regenerate the chart with: python scripts/plot.py")
    elif workers is None:
        print("(--label is not an integer, so nothing was written to the CSV)")

    print()
    print("Paste into scaling_benchmark.md (fill Speedup vs 1 relative to the W=1 row):")
    print(f"| {args.label} | {minutes:.2f} | {throughput:.2f} | — |")


if __name__ == "__main__":
    main()
