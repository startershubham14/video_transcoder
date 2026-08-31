package dev.shubham.transcoder.storage;

import org.springframework.stereotype.Service;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * S3 adapter for {@link BlobStore}. Large video bytes never flow through the API — clients
 * upload directly to S3 via presigned PUT URLs and download via presigned GET URLs; the app
 * only issues those URLs and moves control messages. App code depends on the port, not this
 * class or the AWS SDK.
 *
 * <p>Presigned URLs are minted on demand and never persisted (they expire). Postgres stores
 * the durable object <em>key</em>.
 */
@Service
public class S3BlobStore implements BlobStore {

    @Override
    public List<URL> initiateMultipartUpload(String key, int partCount, Duration ttl) {
        // TODO CreateMultipartUpload + presign each UploadPart.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<String> partETags) {
        // TODO CompleteMultipartUpload (validates every part by ETag).
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        // TODO AbortMultipartUpload.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public long objectSize(String key) {
        // TODO HeadObject.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void download(String key, Path destination) {
        // TODO GetObject to file.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void upload(Path source, String key) {
        // TODO PutObject.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public URL presignGet(String key, Duration ttl) {
        // TODO presign GetObject.
        throw new UnsupportedOperationException("not implemented");
    }
}
