package top.egon.mario.clocktower.agent.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentIntentExecutor {

    private final ClocktowerAgentGameActionService agentGameActionService;
    private final ClocktowerPublicMicService publicMicService;

    public List<ClocktowerGameActionResponse> execute(ClocktowerAgentTaskPo task, AgentIntent intent) {
        if (intent instanceof AgentIntent.PublicSpeech speech) {
            ClocktowerGameActionResponse spoken = submit(task, "PUBLIC_SPEECH", List.of(), null, null,
                    speech.content(), Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            if (!spoken.accepted()) {
                return List.of(spoken);
            }
            ClocktowerGameActionResponse finished = submit(task, "FINISH_SPEECH", List.of(), null, null,
                    null, Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            return List.of(spoken, finished);
        }
        if (intent instanceof AgentIntent.GrabMic grabMic) {
            publicMicService.grabMicAsActor(task.getGameId(), task.getGameSeatId());
            ClocktowerGameActionResponse spoken = submit(task, "PUBLIC_SPEECH", List.of(), null, null,
                    grabMic.reason(), Map.of("policy", HeuristicAgentPolicy.POLICY_NAME, "grabMic", true));
            if (!spoken.accepted()) {
                return List.of(spoken);
            }
            ClocktowerGameActionResponse finished = submit(task, "FINISH_SPEECH", List.of(), null, null,
                    null, Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            return List.of(spoken, finished);
        }
        if (intent instanceof AgentIntent.Pass pass) {
            return List.of(submit(task, "PASS", List.of(), null, null, null,
                    Map.of("passType", "MIC_TURN", "reason", pass.reason())));
        }
        if (intent instanceof AgentIntent.Nominate nominate) {
            return List.of(submit(task, "NOMINATE", List.of(nominate.targetGameSeatId()), null, null,
                    nominate.reason(), Map.of("reason", nominate.reason())));
        }
        if (intent instanceof AgentIntent.Vote vote) {
            return List.of(submit(task, "VOTE", List.of(), vote.nominationId(), vote.vote(),
                    null, Map.of("reason", vote.reason())));
        }
        if (intent instanceof AgentIntent.NightChoice choice) {
            return List.of(submit(task, "NIGHT_CHOICE", choice.targetGameSeatIds(), null, null,
                    null, choice.payload()));
        }
        return List.of();
    }

    private ClocktowerGameActionResponse submit(ClocktowerAgentTaskPo task, String actionType,
                                                List<Long> targetGameSeatIds, Long nominationId,
                                                Boolean vote, String content, Map<String, Object> payload) {
        return agentGameActionService.submitAgentAction(task.getGameId(), task.getAgentInstanceId(),
                new ClocktowerGameActionRequest(task.getGameSeatId(), actionType, targetGameSeatIds,
                        nominationId, vote, content, payload));
    }
}
