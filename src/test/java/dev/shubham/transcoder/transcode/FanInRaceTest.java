package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.job.Job;
import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.job.JobStatus;
import dev.shubham.transcoder.user.User;
import dev.shubham.transcoder.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pipeline's must-test cases around the per-rung fan-in (Golden rule 5), against a real
 * Postgres (Testcontainers) with the actual Flyway schema. The claim is a single guarded
 * {@code UPDATE ... WHERE NOT EXISTS (unfinished segment in this rung)}; these tests pin its
 * behaviour: it never fires before the rung completes, completion triggers exactly one claim, and
 * a redelivered completion / re-claim is idempotent-safe.
 *
 * <p>Note: in production {@code markDone}+{@code tryClaimPackaging} run in one transaction per
 * worker and encodes finish staggered over seconds, so the last committer observes the whole rung
 * DONE and claims once. A redelivered claim on an already-CONCATENATING job matches again (the
 * guard admits CONCATENATING) and re-publishes a PackageTask — harmless, as packaging is idempotent.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=filesystem:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED) // no ambient tx: setup + threads commit on their own
class FanInRaceTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SegmentRepository segments;
    @Autowired
    JobRepository jobs;
    @Autowired
    UserRepository users;
    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        segments.deleteAll();
        jobs.deleteAll();
        users.deleteAll();
    }

    @Test
    void doesNotClaimBeforeTheRungIsComplete_thenClaimsExactlyOnceOnCompletion() {
        String rung = "720p";
        UUID jobId = createProcessingJob();
        List<UUID> ids = createQueuedSegments(jobId, rung, 3);

        // Complete all but the last, then attempt the claim — the guard must NOT fire yet.
        markDoneCommitted(ids.get(0));
        markDoneCommitted(ids.get(1));
        int premature = tx.execute(s -> segments.tryClaimPackaging(jobId, rung));
        assertEquals(0, premature, "claim must not fire while a segment in the rung is unfinished");
        assertEquals(JobStatus.PROCESSING, currentStatus(jobId));

        // The worker that finishes the last segment claims, in one transaction — exactly once.
        int claim = tx.execute(s -> {
            segments.markDone(ids.get(2), key(jobId, rung, 2));
            return segments.tryClaimPackaging(jobId, rung);
        });
        assertEquals(1, claim, "completing the final segment claims packaging exactly once");
        assertEquals(JobStatus.CONCATENATING, currentStatus(jobId));
    }

    @Test
    void redeliveredMarkDoneIsIdempotent() {
        String rung = "480p";
        UUID jobId = createProcessingJob();
        UUID segmentId = createQueuedSegments(jobId, rung, 1).get(0);
        String key = key(jobId, rung, 0);

        int first = tx.execute(s -> segments.markDone(segmentId, key));
        int replay = tx.execute(s -> segments.markDone(segmentId, key)); // redelivery

        assertEquals(1, first, "first completion marks the segment DONE");
        assertEquals(0, replay, "a redelivered completion is a no-op (already DONE)");
        assertEquals(1, segments.findByJobIdAndRung(jobId, rung).size(), "no duplicate segment rows");
        Segment segment = segments.findByJobIdAndRung(jobId, rung).get(0);
        assertEquals(SegmentStatus.DONE, segment.getStatus());
        assertEquals(key, segment.getOutputSegmentKey(), "output key is stable across redelivery");
    }

    @Test
    void concurrentRedeliveryOfTheFinalSegmentIsSafe() throws Exception {
        String rung = "720p";
        int workers = 8;
        UUID jobId = createProcessingJob();
        List<UUID> ids = createQueuedSegments(jobId, rung, 4);
        for (int i = 0; i < 3; i++) {
            markDoneCommitted(ids.get(i)); // pre-complete all but the last
        }
        UUID last = ids.get(3);

        // A redelivery storm of the last segment's task: every worker marks it DONE (idempotent) and
        // claims, in one transaction. The claim admits an already-CONCATENATING job, so several may
        // "win" — that only re-publishes an idempotent PackageTask; state must stay consistent.
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger claims = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                int won = tx.execute(s -> {
                    segments.markDone(last, key(jobId, rung, 3));
                    return segments.tryClaimPackaging(jobId, rung);
                });
                claims.addAndGet(won);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertTrue(claims.get() >= 1, "the completed rung must trigger packaging at least once");
        assertEquals(JobStatus.CONCATENATING, currentStatus(jobId), "job reaches CONCATENATING");
        assertEquals(4, segments.findByJobIdAndRung(jobId, rung).size(), "no duplicate segment rows");
    }

    // --- helpers (each commits in its own transaction so worker threads can see the rows) ---

    private static String key(UUID jobId, String rung, int index) {
        return jobId + "/" + rung + "/" + index + ".ts";
    }

    private UUID createProcessingJob() {
        return tx.execute(s -> {
            User user = users.save(User.create("race-" + UUID.randomUUID() + "@local"));
            Job job = Job.create(user.getId(), Instant.now().plusSeconds(3600));
            job.markPreparing();
            job.markProcessing(); // AWAITING_UPLOAD -> PREPARING -> PROCESSING
            return jobs.save(job).getId();
        });
    }

    private List<UUID> createQueuedSegments(UUID jobId, String rung, int count) {
        return tx.execute(s -> {
            List<UUID> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Segment segment = Segment.create(jobId, Rung.fromLabel(rung), i,
                        jobId + "/segments/" + i + ".ts");
                ids.add(segments.save(segment).getId());
            }
            return ids;
        });
    }

    private void markDoneCommitted(UUID segmentId) {
        tx.executeWithoutResult(s -> segments.markDone(segmentId, "out/" + segmentId + ".ts"));
    }

    private JobStatus currentStatus(UUID jobId) {
        return tx.execute(s -> jobs.findById(jobId).orElseThrow().getStatus());
    }
}
