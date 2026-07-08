package top.egon.mario.clocktower.game.action.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.action.service.ClocktowerGameActionExecutor;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerGameActionExecutorImpl implements ClocktowerGameActionExecutor {

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventAppender eventAppender;
    @SuppressWarnings("unused")
    private final ClocktowerPublicMicService publicMicService;

    @Override
    @Transactional
    public ClocktowerGameActionResponse execute(GameActionCommand command, ActorContext actor) {
        if (command == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ACTION_COMMAND_REQUIRED");
        }
        if (command.gameId() == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        if (command.actorGameSeatId() == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_REQUIRED");
        }
        if (!StringUtils.hasText(command.actionType())) {
            throw new ClocktowerException("CLOCKTOWER_ACTION_TYPE_REQUIRED");
        }
        ClocktowerGamePo game = gameRepository.findLockedByIdAndDeletedFalse(command.gameId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        if (!"RUNNING".equals(game.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_NOT_RUNNING");
        }
        ClocktowerGameSeatPo seat = gameSeatRepository
                .findByIdAndGameIdAndDeletedFalse(command.actorGameSeatId(), game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        if (!"ACTIVE".equals(seat.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INACTIVE");
        }
        if (!actorMatchesSeat(actor, seat)) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
        }
        return switch (command.actionType()) {
            case "PUBLIC_SPEECH", "FINISH_SPEECH", "PASS", "NOMINATE", "VOTE" ->
                    ClocktowerGameActionResponse.rejected("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
            default -> reject(game, seat, command.actionType(), "UNKNOWN_ACTION_TYPE");
        };
    }

    private boolean actorMatchesSeat(ActorContext actor, ClocktowerGameSeatPo seat) {
        if (actor == null) {
            return false;
        }
        if (ClocktowerActorType.HUMAN.equals(actor.actorType())) {
            return ClocktowerActorType.HUMAN.equals(seat.getActorType())
                    && Objects.equals(actor.userId(), seat.getUserId());
        }
        if (ClocktowerActorType.AGENT.equals(actor.actorType())) {
            return ClocktowerActorType.AGENT.equals(seat.getActorType())
                    && Objects.equals(actor.agentInstanceId(), seat.getAgentInstanceId())
                    && Objects.equals(actor.actorId(), seat.getActorId());
        }
        return false;
    }

    private ClocktowerGameActionResponse reject(ClocktowerGamePo game, ClocktowerGameSeatPo seat, String actionType,
                                                String rejectedCode) {
        eventAppender.append(game, "ACTION_REJECTED", seat.getId(), null, "PRIVATE", List.of(seat.getId()),
                Map.of("actionType", actionType, "rejectedCode", rejectedCode), Instant.now());
        return ClocktowerGameActionResponse.rejected(rejectedCode);
    }
}
