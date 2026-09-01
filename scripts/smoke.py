#!/usr/bin/env python3
"""End-to-end smoke test for the transcoding pipeline (stdlib only).

Drives the thin upload handshake against a running stack:
  1. POST /uploads               -> jobId, uploadId, presigned part URLs
  2. PUT each presigned part URL  (bytes go straight to S3/MinIO)
  3. POST /jobs/{id}/complete     -> the API validates the upload and enqueues prepare

Then the pipeline runs on its own (prepare -> transcode -> package). Watch progress via
Postgres / the RabbitMQ + MinIO UIs (the GET /jobs/{id} status endpoint is not built yet).

Usage:
  python scripts/smoke.py <video-file> [api_base]
  # api_base defaults to http://localhost:8080

Requires the presign split (AWS_S3_PUBLIC_ENDPOINT=http://localhost:9000) so the presigned
URLs are reachable from your host. That is the docker-compose default.
"""
import json
import math
import sys
import urllib.error
import urllib.request
from pathlib import Path


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


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    path = Path(sys.argv[1])
    api = (sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080").rstrip("/")
    data = path.read_bytes()
    size = len(data)
    print(f"file={path.name} size={size} bytes  api={api}")

    # 1. create upload
    body = json.dumps({
        "filename": path.name,
        "sizeBytes": size,
        "contentType": "video/mp4",
    }).encode()
    _, raw, _ = _request("POST", f"{api}/uploads",
                         data=body, headers={"Content-Type": "application/json"})
    created = json.loads(raw)
    job_id = created["jobId"]
    upload_id = created["uploadId"]
    part_urls = created["partUrls"]
    print(f"jobId={job_id}  uploadId={upload_id}  parts={len(part_urls)}")

    # 2. PUT each part, collecting ETags in order
    n = len(part_urls)
    chunk = math.ceil(size / n)
    etags = []
    for i, url in enumerate(part_urls):
        part = data[i * chunk:(i + 1) * chunk]
        _, _, resp_headers = _request("PUT", url, data=part,
                                      headers={"Content-Type": "application/octet-stream"})
        etag = resp_headers.get("ETag") or resp_headers.get("Etag")
        if not etag:
            raise SystemExit(f"part {i + 1}: no ETag in response headers {resp_headers}")
        etags.append(etag)
        print(f"  part {i + 1}/{n}: {len(part)} bytes, ETag={etag}")

    # 3. complete -> API validates and enqueues prepare
    body = json.dumps({"uploadId": upload_id, "partETags": etags}).encode()
    status, _, _ = _request("POST", f"{api}/jobs/{job_id}/complete",
                            data=body, headers={"Content-Type": "application/json"})
    print(f"complete -> HTTP {status} (job PREPARING)")
    print()
    print("Now watch it run (packaging is the finish line):")
    print("  RabbitMQ UI : http://localhost:15672  (guest/guest)")
    print("  MinIO console: http://localhost:9001  (minioadmin/minioadmin)")
    print("  Postgres    :")
    print(f"    psql> SELECT status FROM jobs WHERE id='{job_id}';")
    print(f"    psql> SELECT rung, status, output_segment_key FROM segments WHERE job_id='{job_id}' ORDER BY rung, segment_index;")
    print("  Final MP4s land at s3://transcoder/<jobId>/<rung>.mp4 when status=COMPLETED.")


if __name__ == "__main__":
    main()
