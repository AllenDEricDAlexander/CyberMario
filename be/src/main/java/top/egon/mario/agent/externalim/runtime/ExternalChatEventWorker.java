package top.egon.mario.agent.externalim.runtime;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ExternalChatEventWorker {

    private final ExternalChatEventRepository repository;
    private final ExternalChatEventStateService stateService;
    private final ExternalChatEventExecutionService executionService;
    private final MemorySpaceExecutionLane lane;
    private final ExternalChatWorkerProperties properties;

    public ExternalChatEventWorker(ExternalChatEventRepository repository,
                                   ExternalChatEventStateService stateService,
                                   ExternalChatEventExecutionService executionService,
                                   MemorySpaceExecutionLane lane,
                                   ExternalChatWorkerProperties properties) {
        this.repository = repository;
        this.stateService = stateService;
        this.executionService = executionService;
        this.lane = lane;
        this.properties = properties;
    }

    public int processBatch(String workerId) {
        stateService.recoverStale(Instant.now().minus(properties.staleAfter()),
                properties.batchSize(), properties.maxAttempts());
        List<ExternalChatEventPo> ready = repository
                .findByProcessingStatusAndAvailableAtLessThanEqualOrderByReceivedAtAscIdAsc(
                        ExternalChatProcessingStatus.RECEIVED, Instant.now(),
                        PageRequest.of(0, properties.batchSize()));
        int claimed = 0;
        for (ExternalChatEventPo event : ready) {
            if (stateService.claim(event.getId(), workerId)) {
                claimed++;
                lane.submit(event.getSpaceId(), () -> executionService.execute(event.getId(), workerId));
            }
        }
        return claimed;
    }
}
