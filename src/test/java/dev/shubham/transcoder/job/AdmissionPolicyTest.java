package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-logic test of the in-flight admission cap.
 */
class AdmissionPolicyTest {

    private static PipelineProperties propsWithCap(int cap) {
        return new PipelineProperties("mp4", 8, 300, 2_147_483_648L, 3, List.of(2, 8, 30), cap, 60);
    }

    private static AdmissionPolicy policy(long inFlight, int cap) {
        JobRepository repo = mock(JobRepository.class);
        when(repo.countByStatusIn(anyList())).thenReturn(inFlight);
        return new AdmissionPolicy(repo, propsWithCap(cap));
    }

    @Test
    void admitsWhenUnderCap() {
        assertTrue(policy(3, 5).canAdmit());
    }

    @Test
    void rejectsAtOrOverCap() {
        assertFalse(policy(5, 5).canAdmit());
        assertFalse(policy(9, 5).canAdmit());
    }
}
