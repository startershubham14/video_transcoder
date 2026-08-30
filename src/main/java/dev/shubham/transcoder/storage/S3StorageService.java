package dev.shubham.transcoder.storage;

import org.springframework.stereotype.Service;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * All S3 access lives here. Large video bytes never flow through the API — clients
 * upload directly to S3 via presigned PUT URLs and download via presigned GET URLs;
 * the app only issues those URLs and moves control messages.
 *
 * <p>Presigned URLs are minted on demand and never persisted (they expire). Postgres
 * stores the durable object <em>key</em>.
 */
@Service
public class S3StorageService {

    /** Initiate a multipart upload and return per-part presigned PUT URLs. */
    public List<URL> initiateMultipartUpload(String key, int partCount, Duration ttl) {
        // TODO CreateMultipartUpload + presign each UploadPart.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Complete the multipart upload; authoritative success signal for an upload. */
    public void completeMultipartUpload(String key, String uploadId, List<String> partETags) {
        // TODO CompleteMultipartUpload (validates every part by ETag).
        throw new UnsupportedOperationException("not implemented");
    }

    /** Abort a dangling multipart upload (used by the timeout reaper). */
    public void abortMultipartUpload(String key, String uploadId) {
        // TODO AbortMultipartUpload.
        throw new UnsupportedOperationException("not implemented");
    }

    /** HeadObject — size / existence check after completion. */
    public long headObjectSize(String key) {
        // TODO HeadObject.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Download an object to a local working file (worker-side). */
    public void download(String key, Path destination) {
        // TODO GetObject to file.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Upload a local file under a deterministic key (idempotent overwrite). */
    public void upload(Path source, String key) {
        // TODO PutObject.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Mint a fresh presigned GET URL for output delivery. */
    public URL presignGet(String key, Duration ttl) {
        // TODO presign GetObject.
        throw new UnsupportedOperationException("not implemented");
    }
}
