package dev.shubham.transcoder.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic test of {@link Job}'s guarded state machine (no I/O).
 */
class JobTransitionTest {

    private Job newJob() {
        return Job.create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void startsAwaitingUpload() {
        assertEquals(JobStatus.AWAITING_UPLOAD, newJob().getStatus());
        assertFalse(newJob().isTerminal());
    }

    @Test
    void happyPathToCompleted() {
        Job job = newJob();
        job.markPreparing();
        job.markProcessing();
        job.markConcatenating();
        job.markCompleted();
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertTrue(job.isTerminal());
    }

    @Test
    void rejectsIllegalTransition() {
        Job job = newJob(); // AWAITING_UPLOAD cannot jump straight to PROCESSING
        assertThrows(IllegalStateException.class, job::markProcessing);
    }

    @Test
    void rejectsTransitionOutOfTerminal() {
        Job job = newJob();
        job.markExpired();
        assertTrue(job.isTerminal());
        assertThrows(IllegalStateException.class, job::markPreparing);
    }

    @Test
    void failWithRecordsReasonAndIsTerminal() {
        Job job = newJob();
        job.markPreparing();
        job.failWith("corrupt input");
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("corrupt input", job.getErrorReason());
        assertTrue(job.isTerminal());
    }
}
