package dev.shubham.transcoder.transcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Infra-free unit test that keeps {@code mvn verify} meaningful while the pipeline is
 * still a skeleton. A full {@code @SpringBootTest} context-load test needs Postgres /
 * RabbitMQ / S3 (or Testcontainers) and will be added once the stages are implemented.
 */
class RungTest {

    @Test
    void labelsMatchTheDocumentedLadder() {
        assertEquals("720p", Rung.R720P.label());
        assertEquals("480p", Rung.R480P.label());
        assertEquals("360p", Rung.R360P.label());
    }

    @Test
    void heightsDescendAcrossTheLadder() {
        assertTrue(Rung.R720P.height() > Rung.R480P.height());
        assertTrue(Rung.R480P.height() > Rung.R360P.height());
    }
}
