package dev.shubham.transcoder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context loads. TODO: exclude/mock external infrastructure
 * (Postgres, RabbitMQ, S3) or provide Testcontainers so this runs without a live stack.
 */
@SpringBootTest
class TranscoderApplicationTests {

    @Test
    void contextLoads() {
    }
}
