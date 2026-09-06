#!/usr/bin/env python3
"""Scaling-benchmark driver: submit K jobs, time the drain to COMPLETED (stdlib only).

Proves the core claim — transcode throughput scales with the number of workers. Run it once per
worker count and paste the printed table row into scaling_benchmark.md.

  # 1 worker
  docker compose up -d --scale transcode-worker=1
  python scripts/bench.py samples/*.mp4 --label 1

  # 2 workers (bring the tier up, then re-run the same sample set)
  docker compose up -d --scale transcode-worker=2
  python scripts/bench.py samples/*.mp4 --label 2

The clock starts at the first submission and stops when every job reaches a terminal state
(COMPLETED / FAILED / EXPIRED), polling GET /jobs/{id}. Keep everything else constant across runs
(same sample set, SEGMENT_TARGET_SECONDS, ladder, machine). Watch the queue drain live in Grafana
(http://localhost:3000) while it runs.

Usage:
  python scripts/bench.py <video>... [--api URL] [--copies N] [--label W]
                                     [--poll SECONDS] [--timeout SECONDS]
  # --api defaults to http://localhost:8080 ; --copies submits each file N times (default 1)
"""
import argparse
import json
import math
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


def main():
    ap = argparse.ArgumentParser(description="Scaling benchmark: submit K jobs, time the drain.")
    ap.add_argument("videos", nargs="+", type=Path, help="source video file(s)")
    ap.add_argument("--api", default="http://localhost:8080", help="API base URL")
    ap.add_argument("--copies", type=int, default=1, help="submit each file this many times")
    ap.add_argument("--label", default="?", help="worker count, for the results-table row")
    ap.add_argument("--poll", type=float, default=3.0, help="seconds between status polls")
    ap.add_argument("--timeout", type=float, default=1800.0, help="give up after this many seconds")
    args = ap.parse_args()
    api = args.api.rstrip("/")

    files = [v for v in args.videos for _ in range(args.copies)]
    for v in files:
        if not v.is_file():
            raise SystemExit(f"not a file: {v}")
    total = len(files)
    print(f"submitting {total} job(s) to {api} (workers={args.label}) ...")

    start = time.monotonic()
    pending = set()
    for v in files:
        pending.add(submit(api, v))
    print(f"submitted {total} job(s) in {time.monotonic() - start:.1f}s; draining ...")

    failed = []
    while pending:
        if time.monotonic() - start > args.timeout:
            raise SystemExit(f"timeout after {args.timeout:.0f}s; {len(pending)} job(s) still running")
        time.sleep(args.poll)
        for job_id in list(pending):
            state = status_of(api, job_id)
            if state in TERMINAL:
                pending.discard(job_id)
                if state != "COMPLETED":
                    failed.append((job_id, state))
        done = total - len(pending)
        print(f"  {done}/{total} terminal ({time.monotonic() - start:.0f}s)", end="\r", flush=True)

    elapsed = time.monotonic() - start
    minutes = elapsed / 60
    throughput = total / minutes if minutes else float("inf")
    print()
    print(f"done: {total} job(s) in {elapsed:.1f}s ({minutes:.2f} min); "
          f"throughput={throughput:.2f} jobs/min; failed={len(failed)}")
    if failed:
        for job_id, state in failed:
            print(f"  FAILED: {job_id} -> {state}")
    print()
    print("Paste into scaling_benchmark.md (fill Speedup vs 1 relative to the W=1 row):")
    print(f"| {args.label} | {minutes:.2f} | {throughput:.2f} | — |")


if __name__ == "__main__":
    main()
