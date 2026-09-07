package dev.shubham.transcoder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Titles the auto-generated OpenAPI document. springdoc scans the controllers and serves the spec at
 * {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html} — api profile only (workers are
 * headless). Endpoints stay self-documenting; we don't hand-maintain API docs (CLAUDE.md Operations).
 */
@Configuration
@Profile("api")
public class OpenApiConfig {

    @Bean
    OpenAPI transcoderOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Distributed Video Transcoding Pipeline API")
                .version("v1")
                .description("Thin control-plane API: create a presigned multipart upload, complete it, "
                        + "and poll or stream (SSE) job status. Media bytes flow client↔S3 directly, "
                        + "never through the API."));
    }
}
