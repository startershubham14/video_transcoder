package dev.shubham.transcoder.config;

import dev.shubham.transcoder.storage.StorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the AWS SDK v2 clients used by the storage layer: an {@code S3Client} for
 * control-plane calls (initiate/complete multipart, head, get/put) and an {@code S3Presigner}
 * for minting presigned PUT (upload parts) and GET (download) URLs.
 *
 * <p>Honours an optional {@code aws.s3.endpoint} override (path-style access) so the same
 * code targets MinIO locally and real S3 in the cloud. Credentials come from the default
 * provider chain (env {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}); clients are
 * lazy, so no network/credentials are needed at startup.
 */
@Configuration
public class S3Config {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.s3().hasEndpointOverride()) {
            builder.endpointOverride(URI.create(properties.s3().endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder().region(Region.of(properties.region()));
        if (properties.s3().hasEndpointOverride()) {
            builder.endpointOverride(URI.create(properties.s3().endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
