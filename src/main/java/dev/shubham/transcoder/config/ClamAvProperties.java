package dev.shubham.transcoder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the ClamAV daemon ({@code clamd}) that the prepare stage
 * streams the source through before splitting.
 *
 * @param host clamd host
 * @param port clamd TCP port (INSTREAM), default 3310
 */
@ConfigurationProperties(prefix = "clamav")
public record ClamAvProperties(String host, int port) {
}
