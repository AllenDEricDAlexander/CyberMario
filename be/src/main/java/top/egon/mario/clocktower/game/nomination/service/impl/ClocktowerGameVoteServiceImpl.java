package top.egon.mario.clocktower.game.nomination.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameVotePo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameVoteRepository;
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameVoteService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerGameVoteServiceImpl implements ClocktowerGameVoteService {

    private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
    private static final String LIFE_ALIVE = "ALIVE";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CAST = "CAST";

    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameVoteRepository voteRepository;
    private final ClocktowerGameEventAppender eventAppender;

    @Override
    @Transactional
    public ClocktowerGameActionResponse vote(ClocktowerGamePo game,
                                             ClocktowerGameSeatPo actorSeat,
                                             GameActionCommand command,
                                             ActorContext actor) {
        if (!playerSeat(actorSeat)) {
            return reject(game, actorSeat, "CLOCKTOWER_VOTER_INVALID");
        }
        if (command.vote() == null) {
            return reject(game, actorSeat, "CLOCKTOWER_VOTE_VALUE_REQUIRED");
        }
        ClocktowerGameNominationPo nomination = loadOpenNomination(game, command.nominationId());
        if (nomination == null) {
            return reject(game, actorSeat, "CLOCKTOWER_NOMINATION_NOT_OPEN");
        }
        if (voteRepository.existsByNominationIdAndVoterGameSeatIdAndDeletedFalse(
                nomination.getId(), actorSeat.getId())) {
            return reject(game, actorSeat, "CLOCKTOWER_VOTE_ALREADY_CAST");
        }

        boolean voteValue = command.vote();
        boolean usedDeadVote = false;
        if (!alive(actorSeat)) {
            if (voteValue) {
                if (!actorSeat.isHasDeadVote()) {
                    return reject(game, actorSeat, "CLOCKTOWER_DEAD_VOTE_USED");
                }
                usedDeadVote = true;
                actorSeat.setHasDeadVote(false);
                gameSeatRepository.saveAndFlush(actorSeat);
            }
        }

        ClocktowerGameVotePo vote = new ClocktowerGameVotePo();
        vote.setGameId(game.getId());
        vote.setNominationId(nomination.getId());
        vote.setVoterGameSeatId(actorSeat.getId());
        vote.setVoteValue(voteValue);
        vote.setUsedDeadVote(usedDeadVote);
        vote.setStatus(STATUS_CAST);
        voteRepository.saveAndFlush(vote);

        int voteCount = Math.toIntExact(voteRepository.countByNominationIdAndVoteValueTrueAndDeletedFalse(
                nomination.getId()));
        nomination.setVoteCount(voteCount);
        nominationRepository.saveAndFlush(nomination);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nominationId", nomination.getId());
        payload.put("voterGameSeatId", actorSeat.getId());
        payload.put("nomineeGameSeatId", nomination.getNomineeGameSeatId());
        payload.put("voteValue", voteValue);
        payload.put("usedDeadVote", usedDeadVote);
        payload.put("voteCount", voteCount);
        payload.put("actorType", actor.actorType());
        ClocktowerGameEventResponse event = eventAppender.append(game, "VOTE_CAST", actorSeat.getId(),
                nomination.getNomineeGameSeatId(), "PUBLIC", List.of(), payload, Instant.now());
        return ClocktowerGameActionResponse.accepted(event);
    }

    private ClocktowerGameNominationPo loadOpenNomination(ClocktowerGamePo game, Long nominationId) {
        if (nominationId != null) {
            return nominationRepository.findLockedByIdAndGameIdAndDeletedFalse(nominationId, game.getId())
                    .filter(nomination -> STATUS_OPEN.equals(nomination.getStatus()))
                    .orElse(null);
        }
        List<ClocktowerGameNominationPo> openNominations = nominationRepository.findLockedByGameIdAndStatus(
                game.getId(), STATUS_OPEN);
        if (openNominations.isEmpty()) {
            return null;
        }
        return openNominations.getFirst();
    }

    private boolean playerSeat(ClocktowerGameSeatPo seat) {
        return SEAT_STATUS_ACTIVE.equals(seat.getStatus())
                && (ClocktowerActorType.HUMAN.equals(seat.getActorType())
                || ClocktowerActorType.AGENT.equals(seat.getActorType()));
    }

    private boolean alive(ClocktowerGameSeatPo seat) {
        return LIFE_ALIVE.equals(seat.getLifeStatus()) && LIFE_ALIVE.equals(seat.getPublicLifeStatus());
    }

    private ClocktowerGameActionResponse reject(ClocktowerGamePo game, ClocktowerGameSeatPo seat, String rejectedCode) {
        eventAppender.append(game, "ACTION_REJECTED", seat.getId(), null, "PRIVATE", List.of(seat.getId()),
                Map.of("actionType", "VOTE", "rejectedCode", rejectedCode), Instant.now());
        return ClocktowerGameActionResponse.rejected(rejectedCode);
    }
}
