# Distributed Video Transcoding Pipeline

> **Status:**  In design / under construction — architecture is fully specified
> (see [`docs/`](docs/)); implementation in progress.

A distributed video-processing service that ingests a video, splits it into
keyframe-aligned segments, transcodes those segments **in parallel** across a pool of
workers, and packages the result into an adaptive-bitrate **HLS** ladder (with plain MP4
outputs as an intermediate milestone).

The transcoding itself is a solved problem (FFmpeg) — the focus of this project is the
**distributed job-processing system around it**: an async job queue, a horizontally
scalable worker pool, fan-out/fan-in coordination, failure handling, and backpressure.

## Stack

**Spring Boot** (Spring Web + Spring AMQP) · **RabbitMQ** · **PostgreSQL** (Spring Data
JPA) · **AWS S3** (SDK v2) · **ClamAV** · **Docker / docker-compose**. Runs entirely
locally; workers are `@RabbitListener` consumers scaled by replica count.

## How it works

Bytes never flow through the API — the client uploads directly to S3 via presigned
multipart URLs. The API and queue carry only small control messages; **PostgreSQL is the
source of truth for job state**. The pipeline is three stages, each a queue + a listener:
`prepare` (probe → scan → split → fan-out) → `transcode` (per-segment, parallel) →
`concat/package` (per-rung fan-in → MP4 / HLS).

### Architecture

```mermaid
flowchart LR
    Client(["Client"])
    RP["nginx reverse proxy<br/>rate-limit · conn-cap · TLS · body-size"]

    subgraph API["Spring Boot API"]
        REST["REST Controllers"]
        ADM["Admission control<br/>in-flight cap to 429"]
        SSE["SSE / WebSocket push"]
    end

    subgraph MQ["RabbitMQ"]
        PQ[["prepare.queue"]]
        SQ[["transcode.queue"]]
        CQ[["concat.queue"]]
        RQ[["retry.delay.queue<br/>TTL backoff"]]
        DLQ[["dead-letter.queue"]]
    end

    subgraph PrepW["Prepare consumers (few)"]
        WP["Prepare worker"]
    end
    subgraph TransW["Transcode consumers (scaled)"]
        WT["Transcode worker · FFmpeg"]
    end

    DB[("PostgreSQL<br/>jobs + segments")]
    S3[("AWS S3")]
    AV["ClamAV (clamd)"]

    Client -->|"POST /uploads, /complete"| RP
    RP --> REST
    REST --> ADM
    REST -->|"presigned URLs"| Client
    REST -->|"CompleteMultipartUpload (validate)"| S3
    Client -->|"multipart bytes"| S3
    REST <-->|"state"| DB
    REST -->|"enqueue prepare"| PQ

    PQ --> WP
    WP <-->|"source / segments"| S3
    WP -->|"scan before split"| AV
    WP <-->|"state"| DB
    WP -->|"fan-out N"| SQ

    SQ --> WT
    WT <-->|"pull / write"| S3
    WT <-->|"state"| DB
    WT -->|"last done to concat"| CQ
    CQ --> WT

    SQ -.->|"transient fail"| RQ
    RQ -.->|"after TTL"| SQ
    SQ -.->|"permanent / N exhausted"| DLQ

    DB -.-> SSE
    SSE -.->|"push URLs if active"| Client
    Client -.->|"poll if gone"| RP
```

### End-to-end flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as Spring Boot API
    participant S3 as AWS S3
    participant DB as PostgreSQL
    participant MQ as RabbitMQ
    participant W as Workers

    Note over C,API: Upload phase (thin API)
    C->>API: POST /uploads with filename and size
    API->>DB: create job (AWAITING_UPLOAD)
    API->>S3: initiate multipart upload
    API-->>C: job_id plus presigned part URLs
    C->>S3: upload parts in parallel
    C->>API: POST /jobs/{id}/complete
    API->>S3: CompleteMultipartUpload
    API->>MQ: enqueue PREPARE task
    API-->>C: 202 Accepted (PREPARING)

    Note over W,MQ: Stage 1 - prepare
    MQ->>W: PREPARE task
    W->>S3: download source
    W->>W: ffprobe, ClamAV scan, limit checks
    alt invalid, infected, or over limits
        W->>DB: job FAILED with reason
    else clean
        W->>S3: split into keyframe segments
        W->>DB: insert segment rows per rung (QUEUED)
        W->>MQ: fan-out TRANSCODE tasks
        W->>DB: job PROCESSING
    end

    Note over W,MQ: Stage 2 - transcode (parallel)
    loop each segment
        MQ->>W: TRANSCODE task
        W->>S3: download segment
        W->>W: FFmpeg encode to rung
        W->>S3: upload encoded segment
        W->>DB: segment DONE, atomic per-rung last check
        W->>MQ: ack
    end

    Note over W,DB: Stage 3 - concat and package (per-rung fan-in)
    W->>MQ: publish CONCAT or PACKAGE task per rung
    MQ->>W: task
    W->>S3: download that rung's segments
    W->>W: concat to MP4 or package to HLS
    W->>S3: upload outputs plus master m3u8 for HLS
    W->>DB: rung done, all rungs done means job COMPLETED

    C->>API: GET /jobs/{id} to poll
    API->>DB: read job and segment states
    API-->>C: progress percent or COMPLETED with URLs
```

### Checking progress

`GET /jobs/{id}` returns the job's status, a segment-derived `progress` (0–100), and — once the
job is `COMPLETED` — freshly minted presigned download URLs (one per rung for MP4; the master
manifest for HLS). URLs are never stored; they are signed on demand and expire
(`DOWNLOAD_URL_TTL_MINUTES`). Unknown ids return `404`. (Live SSE push at `/jobs/{id}/events` is
planned; polling is the baseline.)

## Documentation

- [`docs/design-notes.md`](docs/design-notes.md) — the **why**: project concept, stack
  rationale, infrastructure decisions, learning plan.
- [`docs/architecture-and-workflow.md`](docs/architecture-and-workflow.md) — the **how**:
  full diagrams, database schema + the atomic fan-in query, queue reliability, input
  limits, and the build order.

## Planned repository layout

```
.
├── docs/                     # design notes + architecture
├── src/main/java/...         # Spring Boot API + workers (same codebase, profiles)
├── db/migrations/            # schema migrations (Flyway)
├── .github/workflows/ci.yml  # CI: ./mvnw verify on every PR
├── docker-compose.yml        # api, worker, rabbitmq, postgres, clamav, (minio)
├── .env.example              # config template (no secrets)
└── README.md
```

## Getting started

> Coming soon — `docker-compose up` will bring up the full stack locally.
> Configuration is env-driven; copy `.env.example` to `.env` and fill in values.

## Security note

Secrets are never committed. AWS access is via a **dedicated, least-privilege IAM user**
scoped to a single bucket; credentials live only in a local, git-ignored `.env`. See the
design notes for the full secrets/IAM approach.
