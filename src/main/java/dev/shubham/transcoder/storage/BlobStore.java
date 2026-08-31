package dev.shubham.transcoder.storage;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Port for object storage. Application code depends on this, never on a vendor SDK
 * directly, so the S3 adapter can be swapped (MinIO ↔ AWS) and mocked in tests.
 * The concrete adapter is {@link S3BlobStore}.
 */
public interface BlobStore {

    /** Initiate a multipart upload and return per-part presigned PUT URLs. */
    List<URL> initiateMultipartUpload(String key, int partCount, Duration ttl);

    /** Complete the multipart upload; authoritative success signal for an upload. */
    void completeMultipartUpload(String key, String uploadId, List<String> partETags);

    /** Abort a dangling multipart upload (used by the timeout reaper). */
    void abortMultipartUpload(String key, String uploadId);

    /** Object size / existence check after completion (HeadObject under the hood). */
    long objectSize(String key);

    /** Download an object to a local working file (worker-side). */
    void download(String key, Path destination);

    /** Upload a local file under a deterministic key (idempotent overwrite). */
    void upload(Path source, String key);

    /** Mint a fresh presigned GET URL for output delivery. */
    URL presignGet(String key, Duration ttl);
}
