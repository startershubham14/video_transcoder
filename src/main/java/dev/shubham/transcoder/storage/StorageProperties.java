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

    /**
     * @param bucket         S3 bucket
     * @param endpoint       internal endpoint the app uses for control-plane calls (e.g.
     *                       {@code http://minio:9000}); blank for real AWS
     * @param partSizeBytes  multipart part size
     * @param publicEndpoint client-reachable endpoint used only when minting presigned URLs
     *                       (e.g. {@code http://localhost:9000} for a host client); blank →
     *                       fall back to {@code endpoint}. Presigned URLs must be reachable by
     *                       whoever uploads/downloads, which is not always the app's endpoint.
     */
    public record S3(String bucket, String endpoint, long partSizeBytes, String publicEndpoint) {

        /** Whether a custom (MinIO-style) endpoint override is configured for app calls. */
        public boolean hasEndpointOverride() {
            return endpoint != null && !endpoint.isBlank();
        }

        /** Endpoint to embed in presigned URLs — the public one if set, else the internal one. */
        public String presignEndpoint() {
            return (publicEndpoint != null && !publicEndpoint.isBlank()) ? publicEndpoint : endpoint;
        }

        public boolean hasPresignEndpoint() {
            String e = presignEndpoint();
            return e != null && !e.isBlank();
        }
    }
}
