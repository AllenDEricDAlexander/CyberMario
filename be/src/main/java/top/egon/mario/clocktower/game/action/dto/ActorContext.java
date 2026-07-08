package top.egon.mario.clocktower.game.action.dto;

import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;

public record ActorContext(
        String actorType,
        Long actorId,
        Long userId,
        Long agentInstanceId,
        boolean systemInternal
) {

    public static ActorContext human(Long actorId, Long userId) {
        return new ActorContext(ClocktowerActorType.HUMAN, actorId, userId, null, false);
    }

    public static ActorContext agent(Long actorId, Long agentInstanceId) {
        return new ActorContext(ClocktowerActorType.AGENT, actorId, null, agentInstanceId, false);
    }

    public static ActorContext storyteller(Long userId) {
        return new ActorContext(ClocktowerActorType.STORYTELLER, null, userId, null, false);
    }

    public static ActorContext system() {
        return new ActorContext(ClocktowerActorType.SYSTEM, null, null, null, true);
    }
}
