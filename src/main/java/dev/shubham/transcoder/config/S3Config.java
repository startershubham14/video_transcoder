package dev.shubham.transcoder.config;

import org.springframework.context.annotation.Configuration;

/**
 * Builds the AWS SDK v2 clients used by the storage layer: an {@code S3Client} for
 * control-plane calls (initiate/complete multipart, head) and an {@code S3Presigner}
 * for minting presigned PUT (upload parts) and GET (download) URLs.
 *
 * <p>Honours an optional {@code aws.s3.endpoint} override so the same code targets
 * MinIO locally and real S3 in the cloud.
 *
 * <p>TODO: expose {@code S3Client} and {@code S3Presigner} beans wired to region,
 * credentials and the optional endpoint override.
 */
@Configuration
public class S3Config {

    // TODO @Bean S3Client and @Bean S3Presigner (path-style access when an endpoint is set).
}
