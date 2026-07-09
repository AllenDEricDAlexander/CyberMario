package top.egon.mario.clocktower.agent.strategy;

import java.util.List;
import java.util.Map;

public sealed interface AgentIntent {

    record PublicSpeech(String content) implements AgentIntent {
    }

    record GrabMic(String reason) implements AgentIntent {
    }

    record FinishSpeech(String reason) implements AgentIntent {
    }

    record Nominate(Long targetGameSeatId, String reason) implements AgentIntent {
    }

    record Vote(Long nominationId, boolean vote, String reason) implements AgentIntent {
    }

    record NightChoice(Long taskId, List<Long> targetGameSeatIds, Map<String, Object> payload)
            implements AgentIntent {
    }

    record Pass(String reason) implements AgentIntent {
    }

    record Noop(String reason) implements AgentIntent {
    }
}
