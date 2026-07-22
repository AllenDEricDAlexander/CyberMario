package top.egon.mario.agent.externalim.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ExternalChatEventWorkerTests {

    @Test
    void recoversThenSubmitsReadyRowsInRepositoryOrder() {
        ExternalChatEventRepository repository = mock(ExternalChatEventRepository.class);
        ExternalChatEventStateService stateService = mock(ExternalChatEventStateService.class);
        ExternalChatEventExecutionService executionService = mock(ExternalChatEventExecutionService.class);
        MemorySpaceExecutionLane lane = mock(MemorySpaceExecutionLane.class);
        ExternalChatWorkerProperties properties = new ExternalChatWorkerProperties(
                true, 20, 3, Duration.ZERO, Duration.ofSeconds(1),
                Duration.ofSeconds(5), Duration.ofMinutes(2));
        ExternalChatEventWorker worker = new ExternalChatEventWorker(
                repository, stateService, executionService, lane, properties);
        ExternalChatEventPo first = receivedEvent(10L, "space-a",
                Instant.parse("2026-07-20T00:00:00Z"));
        ExternalChatEventPo second = receivedEvent(11L, "space-a",
                Instant.parse("2026-07-20T00:00:01Z"));
        given(repository.findByProcessingStatusAndAvailableAtLessThanEqualOrderByReceivedAtAscIdAsc(
                eq(ExternalChatProcessingStatus.RECEIVED), any(Instant.class),
                eq(PageRequest.of(0, 20)))).willReturn(List.of(first, second));
        given(stateService.claim(anyLong(), eq("worker-1"))).willReturn(true);
        given(lane.submit(eq("space-a"), any(Runnable.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        assertThat(worker.processBatch("worker-1")).isEqualTo(2);

        var order = inOrder(stateService, repository, lane);
        order.verify(stateService).recoverStale(any(Instant.class), eq(20), eq(3));
        order.verify(repository).findByProcessingStatusAndAvailableAtLessThanEqualOrderByReceivedAtAscIdAsc(
                eq(ExternalChatProcessingStatus.RECEIVED), any(Instant.class),
                eq(PageRequest.of(0, 20)));
        order.verify(stateService).claim(10L, "worker-1");
        order.verify(lane).submit(eq("space-a"), any(Runnable.class));
        order.verify(stateService).claim(11L, "worker-1");
        order.verify(lane).submit(eq("space-a"), any(Runnable.class));
    }

    private ExternalChatEventPo receivedEvent(Long id, String spaceId, Instant receivedAt) {
        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setId(id);
        event.setSpaceId(spaceId);
        event.setProcessingStatus(ExternalChatProcessingStatus.RECEIVED);
        event.setReceivedAt(receivedAt);
        event.setAvailableAt(receivedAt);
        return event;
    }
}
