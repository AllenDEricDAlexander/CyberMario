package top.egon.mario.agent.externalim.runtime;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExternalChatEventStateServiceTests {

    private final ExternalChatEventRepository repository = mock(ExternalChatEventRepository.class);
    private final ExternalChatEventStateService service = new ExternalChatEventStateService(repository);

    @Test
    void retryKeepsCandidateAndReleasesClaimUntilAttemptLimit() {
        ExternalChatEventPo event = runningEvent();
        event.setAssistantMessageId(501L);
        given(repository.findById(10L)).willReturn(Optional.of(event));
        Instant availableAt = Instant.parse("2026-07-20T00:01:00Z");

        service.retryReply(10L, "worker-1", "RATE_LIMIT", "later", availableAt, 3);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getProcessingStatus()).isEqualTo(ExternalChatProcessingStatus.RECEIVED);
        assertThat(event.getReplyStatus()).isEqualTo(ExternalChatReplyStatus.RETRY_PENDING);
        assertThat(event.getAssistantMessageId()).isEqualTo(501L);
        assertThat(event.getAvailableAt()).isEqualTo(availableAt);
        assertThat(event.getLockedBy()).isNull();
        verify(repository).save(event);
    }

    @Test
    void retryAtAttemptLimitBecomesTerminalFailure() {
        ExternalChatEventPo event = runningEvent();
        event.setAttempts(2);
        given(repository.findById(10L)).willReturn(Optional.of(event));

        service.retryReply(10L, "worker-1", "RATE_LIMIT", "still failing", Instant.now(), 3);

        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getProcessingStatus()).isEqualTo(ExternalChatProcessingStatus.FAILED);
        assertThat(event.getReplyStatus()).isEqualTo(ExternalChatReplyStatus.FAILED);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    void staleRecoveryPreservesCandidateForCandidateOnlyRetry() {
        ExternalChatEventPo event = runningEvent();
        event.setAssistantMessageId(501L);
        given(repository.findByProcessingStatusAndLockedAtLessThanOrderByLockedAtAscIdAsc(
                any(), any(), any())).willReturn(List.of(event));

        assertThat(service.recoverStale(Instant.now(), 20, 3)).isEqualTo(1);

        assertThat(event.getProcessingStatus()).isEqualTo(ExternalChatProcessingStatus.RECEIVED);
        assertThat(event.getReplyStatus()).isEqualTo(ExternalChatReplyStatus.RETRY_PENDING);
        assertThat(event.getAssistantMessageId()).isEqualTo(501L);
        assertThat(event.getLockedBy()).isNull();
    }

    private ExternalChatEventPo runningEvent() {
        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setId(10L);
        event.setProcessingStatus(ExternalChatProcessingStatus.RUNNING);
        event.setReplyStatus(ExternalChatReplyStatus.PENDING);
        event.setLockedAt(Instant.now());
        event.setLockedBy("worker-1");
        return event;
    }
}
