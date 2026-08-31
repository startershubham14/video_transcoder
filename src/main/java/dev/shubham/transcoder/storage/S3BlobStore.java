package dev.shubham.transcoder.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3BlobStore(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = properties.s3().bucket();
    }

    @Override
    public PresignedMultipartUpload initiateMultipartUpload(String key, int partCount, Duration ttl) {
        String uploadId = s3Client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder().bucket(bucket).key(key).build())
                .uploadId();

        List<URL> partUrls = new ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            UploadPartRequest part = UploadPartRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber).build();
            URL url = s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                    .signatureDuration(ttl).uploadPartRequest(part).build()).url();
            partUrls.add(url);
        }
        return new PresignedMultipartUpload(uploadId, partUrls);
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<String> partETags) {
        List<CompletedPart> parts = new ArrayList<>(partETags.size());
        for (int i = 0; i < partETags.size(); i++) {
            parts.add(CompletedPart.builder().partNumber(i + 1).eTag(partETags.get(i)).build());
        }
        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                .build());
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId).build());
    }

    @Override
    public long objectSize(String key) {
        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                .contentLength();
    }

    @Override
    public void download(String key, Path destination) {
        s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                ResponseTransformer.toFile(destination));
    }

    @Override
    public void upload(Path source, String key) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromFile(source));
    }

    @Override
    public URL presignGet(String key, Duration ttl) {
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()).url();
    }
}
