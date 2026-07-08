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
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameNominationService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerGameActionExecutorImpl implements ClocktowerGameActionExecutor {

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventAppender eventAppender;
    private final ClocktowerPublicMicService publicMicService;
    private final ClocktowerGameNominationService nominationService;

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
            case "PUBLIC_SPEECH" -> publicSpeech(game, seat, command, actor);
            case "FINISH_SPEECH" -> finishSpeech(game, seat, actor);
            case "PASS" -> pass(game, seat, command, actor);
            case "NOMINATE" -> nominationService.nominate(game, seat, command, actor);
            case "VOTE" ->
                    ClocktowerGameActionResponse.rejected("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
            default -> reject(game, seat, command.actionType(), "UNKNOWN_ACTION_TYPE");
        };
    }

    private ClocktowerGameActionResponse publicSpeech(ClocktowerGamePo game, ClocktowerGameSeatPo seat,
                                                      GameActionCommand command, ActorContext actor) {
        if (!speechPhase(game)) {
            return reject(game, seat, "PUBLIC_SPEECH", "CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID");
        }
        if (!StringUtils.hasText(command.content())) {
            return reject(game, seat, "PUBLIC_SPEECH", "CLOCKTOWER_PUBLIC_SPEECH_CONTENT_REQUIRED");
        }
        if (command.content().length() > 1000) {
            return reject(game, seat, "PUBLIC_SPEECH", "CLOCKTOWER_PUBLIC_SPEECH_CONTENT_TOO_LONG");
        }
        try {
            publicMicService.requireCanSpeak(game.getId(), seat.getId());
        } catch (ClocktowerException ex) {
            return reject(game, seat, "PUBLIC_SPEECH", ex.getMessage());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", command.content().trim());
        payload.put("actorType", actor.actorType());
        if (command.payload() != null && !command.payload().isEmpty()) {
            payload.put("clientPayload", command.payload());
        }
        ClocktowerGameEventResponse event = eventAppender.append(game, "PUBLIC_SPEECH", seat.getId(), null,
                "PUBLIC", List.of(), payload, Instant.now());
        return ClocktowerGameActionResponse.accepted(event);
    }

    private ClocktowerGameActionResponse finishSpeech(ClocktowerGamePo game, ClocktowerGameSeatPo seat,
                                                      ActorContext actor) {
        if (!speechPhase(game)) {
            return reject(game, seat, "FINISH_SPEECH", "CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID");
        }
        ClocktowerGameActionResponse rejected = rejectIfCannotSpeak(game, seat, "FINISH_SPEECH");
        if (rejected != null) {
            return rejected;
        }
        ClocktowerGameEventResponse event = eventAppender.append(game, "FINISH_SPEECH", seat.getId(), null,
                "PUBLIC", List.of(), Map.of("actorType", actor.actorType()), Instant.now());
        publicMicService.finishCurrentTurnAsActor(game.getId(), seat.getId());
        return ClocktowerGameActionResponse.accepted(event);
    }

    private ClocktowerGameActionResponse pass(ClocktowerGamePo game, ClocktowerGameSeatPo seat,
                                              GameActionCommand command, ActorContext actor) {
        String passType = stringPayload(command.payload(), "passType");
        if (!"MIC_TURN".equals(passType)) {
            return reject(game, seat, "PASS", "CLOCKTOWER_PASS_TYPE_UNSUPPORTED");
        }
        if (!speechPhase(game)) {
            return reject(game, seat, "PASS", "CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID");
        }
        ClocktowerGameActionResponse rejected = rejectIfCannotSpeak(game, seat, "PASS");
        if (rejected != null) {
            return rejected;
        }
        ClocktowerGameEventResponse event = eventAppender.append(game, "PLAYER_PASSED", seat.getId(), null,
                "PUBLIC", List.of(), Map.of("passType", "MIC_TURN", "actorType", actor.actorType()), Instant.now());
        publicMicService.finishCurrentTurnAsActor(game.getId(), seat.getId());
        return ClocktowerGameActionResponse.accepted(event);
    }

    private ClocktowerGameActionResponse rejectIfCannotSpeak(ClocktowerGamePo game, ClocktowerGameSeatPo seat,
                                                             String actionType) {
        try {
            publicMicService.requireCanSpeak(game.getId(), seat.getId());
            return null;
        } catch (ClocktowerException ex) {
            return reject(game, seat, actionType, ex.getMessage());
        }
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

    private boolean speechPhase(ClocktowerGamePo game) {
        return "DAY".equals(game.getPhase()) || "NOMINATION".equals(game.getPhase());
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private ClocktowerGameActionResponse reject(ClocktowerGamePo game, ClocktowerGameSeatPo seat, String actionType,
                                                String rejectedCode) {
        eventAppender.append(game, "ACTION_REJECTED", seat.getId(), null, "PRIVATE", List.of(seat.getId()),
                Map.of("actionType", actionType, "rejectedCode", rejectedCode), Instant.now());
        return ClocktowerGameActionResponse.rejected(rejectedCode);
    }
}
