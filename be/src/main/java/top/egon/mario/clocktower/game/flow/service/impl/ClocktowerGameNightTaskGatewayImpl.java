package top.egon.mario.clocktower.game.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameNightTaskSummary;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameNightTaskGateway;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerGameNightTaskGatewayImpl implements ClocktowerGameNightTaskGateway {

    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final Set<String> COMPLETED_STATUSES = Set.of(STATUS_DONE, STATUS_SKIPPED);

    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ClocktowerGameNightTaskService nightTaskService;

    @Override
    public ClocktowerGameNightTaskSummary summarize(ClocktowerGamePo game) {
        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
        int mandatoryCount = (int) tasks.stream().filter(ClocktowerGameNightTaskPo::isMandatory).count();
        int doneCount = (int) tasks.stream().filter(task -> STATUS_DONE.equals(task.getStatus())).count();
        int skippedCount = (int) tasks.stream().filter(task -> STATUS_SKIPPED.equals(task.getStatus())).count();
        int pendingMandatoryCount = (int) tasks.stream()
                .filter(ClocktowerGameNightTaskPo::isMandatory)
                .filter(task -> !COMPLETED_STATUSES.contains(task.getStatus()))
                .count();
        return new ClocktowerGameNightTaskSummary(mandatoryCount, pendingMandatoryCount, doneCount, skippedCount);
    }

    @Override
    public void initializeNightTasks(ClocktowerGamePo game) {
        nightTaskService.initializeNightTasks(game);
    }
}
