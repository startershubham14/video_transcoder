package dev.shubham.transcoder.job;

import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SSE registry / push. Real {@link SseEmitter}s are used: sends and completes
 * before a request is attached are buffered by Spring, not thrown, so no broker/servlet is needed.
 */
class OutputDeliveryServiceTest {

    private static JobStatusResponse snapshot(UUID id, JobStatus status) {
        return new JobStatusResponse(id, status, status == JobStatus.COMPLETED ? 100 : 40, null, List.of());
    }

    @Test
    void keepsEmitterRegisteredWhileJobIsInProgress() {
        UUID id = UUID.randomUUID();
        JobStatusService jobStatusService = mock(JobStatusService.class);
        OutputDeliveryService service = new OutputDeliveryService(jobStatusService);

        service.register(id, new SseEmitter(), snapshot(id, JobStatus.PROCESSING));
        assertTrue(service.hasEmitters(id));

        when(jobStatusService.getStatus(id)).thenReturn(snapshot(id, JobStatus.PROCESSING));
        service.onJobEvent(id);
        assertTrue(service.hasEmitters(id)); // still streaming
    }

    @Test
    void completesAndDeregistersOnTerminalStatus() {
        UUID id = UUID.randomUUID();
        JobStatusService jobStatusService = mock(JobStatusService.class);
        OutputDeliveryService service = new OutputDeliveryService(jobStatusService);

        service.register(id, new SseEmitter(), snapshot(id, JobStatus.PROCESSING));
        assertTrue(service.hasEmitters(id));

        when(jobStatusService.getStatus(id)).thenReturn(snapshot(id, JobStatus.COMPLETED));
        service.onJobEvent(id);

        assertFalse(service.hasEmitters(id)); // completed → removed
    }

    @Test
    void registeringWithAnAlreadyTerminalSnapshotCompletesImmediately() {
        UUID id = UUID.randomUUID();
        OutputDeliveryService service = new OutputDeliveryService(mock(JobStatusService.class));

        service.register(id, new SseEmitter(), snapshot(id, JobStatus.COMPLETED));

        assertFalse(service.hasEmitters(id));
    }

    @Test
    void eventForJobWithNoSubscribersIsANoOp() {
        JobStatusService jobStatusService = mock(JobStatusService.class);
        OutputDeliveryService service = new OutputDeliveryService(jobStatusService);

        service.onJobEvent(UUID.randomUUID()); // must not throw or query the DB
    }
}
