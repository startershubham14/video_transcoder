package dev.shubham.transcoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>The same artifact runs as both the API and the workers; behaviour is selected by
 * Spring profile / configuration, not by separate builds. {@code @EnableScheduling}
 * powers the reconciliation sweep and the upload-timeout reaper.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TranscoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranscoderApplication.class, args);
    }
}
