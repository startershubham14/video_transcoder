package dev.shubham.transcoder.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 / object-storage settings bound from {@code aws.*}. An empty {@code s3.endpoint} means
 * real AWS; a non-empty one (e.g. {@code http://minio:9000}) targets an S3-compatible store
 * with path-style access.
 *
 * @param region AWS region
 * @param s3     bucket + optional endpoint override + multipart part size
 */
@ConfigurationProperties(prefix = "aws")
public record StorageProperties(String region, S3 s3) {

    public record S3(String bucket, String endpoint, long partSizeBytes) {

        /** Whether a custom (MinIO-style) endpoint override is configured. */
        public boolean hasEndpointOverride() {
            return endpoint != null && !endpoint.isBlank();
        }
    }
}
