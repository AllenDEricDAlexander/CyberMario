package top.egon.mario.clocktower.game.nomination.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
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
public class ClocktowerGameNominationServiceImpl implements ClocktowerGameNominationService {

    private static final String PHASE_DAY = "DAY";
    private static final String PHASE_NOMINATION = "NOMINATION";
    private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
    private static final String LIFE_ALIVE = "ALIVE";
    private static final String STATUS_OPEN = "OPEN";
    private static final String SESSION_CLOSED = "CLOSED";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGamePublicMicSessionRepository micSessionRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameEventAppender eventAppender;

    @Override
    @Transactional
    public ClocktowerGameActionResponse nominate(ClocktowerGamePo game,
                                                 ClocktowerGameSeatPo actorSeat,
                                                 GameActionCommand command,
                                                 ActorContext actor) {
        if (!PHASE_DAY.equals(game.getPhase()) && !PHASE_NOMINATION.equals(game.getPhase())) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_PHASE_INVALID");
        }
        if (!playerSeat(actorSeat) || !alive(actorSeat)) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATOR_INVALID");
        }
        Long nomineeSeatId = firstTarget(command.targetGameSeatIds());
        if (nomineeSeatId == null) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_TARGET_REQUIRED");
        }
        if (Objects.equals(actorSeat.getId(), nomineeSeatId)) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_SELF_NOT_ALLOWED");
        }
        ClocktowerGameSeatPo nominee = gameSeatRepository
                .findByIdAndGameIdAndDeletedFalse(nomineeSeatId, game.getId())
                .orElse(null);
        if (nominee == null || !playerSeat(nominee) || !alive(nominee)) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINEE_INVALID");
        }
        if (!micClosedIfPresent(game)) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_MIC_SESSION_OPEN");
        }
        if (!nominationRepository.findLockedByGameIdAndStatus(game.getId(), STATUS_OPEN).isEmpty()) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_OPEN_EXISTS");
        }
        if (nominationRepository.existsByGameIdAndDayNoAndNominatorGameSeatIdAndDeletedFalse(
                game.getId(), game.getDayNo(), actorSeat.getId())) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED");
        }
        if (nominationRepository.existsByGameIdAndDayNoAndNomineeGameSeatIdAndDeletedFalse(
                game.getId(), game.getDayNo(), nominee.getId())) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINEE_ALREADY_NOMINATED");
        }

        Instant now = Instant.now();
        int requiredVotes = requiredVotes(game.getId());
        ClocktowerGameNominationPo nomination = new ClocktowerGameNominationPo();
        nomination.setGameId(game.getId());
        nomination.setOpenGameId(game.getId());
        nomination.setDayNo(game.getDayNo());
        nomination.setNominatorGameSeatId(actorSeat.getId());
        nomination.setNomineeGameSeatId(nominee.getId());
        nomination.setStatus(STATUS_OPEN);
        nomination.setVoteCount(0);
        nomination.setRequiredVotes(requiredVotes);
        nomination.setOpenedAt(now);
        nomination = nominationRepository.saveAndFlush(nomination);

        if (!PHASE_NOMINATION.equals(game.getPhase())) {
            game.setPhase(PHASE_NOMINATION);
            gameRepository.saveAndFlush(game);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nominationId", nomination.getId());
        payload.put("nominatorGameSeatId", actorSeat.getId());
        payload.put("nomineeGameSeatId", nominee.getId());
        payload.put("requiredVotes", nomination.getRequiredVotes());
        payload.put("voteCount", nomination.getVoteCount());
        payload.put("actorType", actor.actorType());
        if (StringUtils.hasText(command.content())) {
            payload.put("content", command.content().trim());
        }
        ClocktowerGameEventResponse event = eventAppender.append(game, "NOMINATION_OPENED", actorSeat.getId(),
                nominee.getId(), "PUBLIC", List.of(), payload, now);
        return ClocktowerGameActionResponse.accepted(event);
    }

    private Long firstTarget(List<Long> targetGameSeatIds) {
        if (targetGameSeatIds == null || targetGameSeatIds.isEmpty()) {
            return null;
        }
        return targetGameSeatIds.getFirst();
    }

    private boolean playerSeat(ClocktowerGameSeatPo seat) {
        return SEAT_STATUS_ACTIVE.equals(seat.getStatus())
                && (ClocktowerActorType.HUMAN.equals(seat.getActorType())
                || ClocktowerActorType.AGENT.equals(seat.getActorType()));
    }

    private boolean alive(ClocktowerGameSeatPo seat) {
        return LIFE_ALIVE.equals(seat.getLifeStatus()) && LIFE_ALIVE.equals(seat.getPublicLifeStatus());
    }

    private boolean micClosedIfPresent(ClocktowerGamePo game) {
        return micSessionRepository.findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .map(ClocktowerGamePublicMicSessionPo::getStatus)
                .map(SESSION_CLOSED::equals)
                .orElse(true);
    }

    private int requiredVotes(Long gameId) {
        long aliveCount = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId)
                .stream()
                .filter(this::playerSeat)
                .filter(this::alive)
                .count();
        return Math.toIntExact((aliveCount + 1) / 2);
    }

    private ClocktowerGameActionResponse reject(ClocktowerGamePo game, ClocktowerGameSeatPo seat, String rejectedCode) {
        eventAppender.append(game, "ACTION_REJECTED", seat.getId(), null, "PRIVATE", List.of(seat.getId()),
                Map.of("actionType", "NOMINATE", "rejectedCode", rejectedCode), Instant.now());
        return ClocktowerGameActionResponse.rejected(rejectedCode);
    }
}
