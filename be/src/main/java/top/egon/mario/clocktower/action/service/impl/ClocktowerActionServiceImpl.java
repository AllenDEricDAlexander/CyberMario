package top.egon.mario.clocktower.action.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.action.dto.ClocktowerActionRequest;
import top.egon.mario.clocktower.action.dto.ClocktowerActionResponse;
import top.egon.mario.clocktower.action.service.ClocktowerActionService;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerVotePo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerActionServiceImpl implements ClocktowerActionService {

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerNominationRepository nominationRepository;
    private final ClocktowerVoteRepository voteRepository;
    private final ClocktowerEventService eventService;

    @Override
    @Transactional
    public ClocktowerActionResponse submit(Long roomId, ClocktowerActionRequest request, RbacPrincipal principal) {
        ClocktowerAccess.requireAuthenticated(principal);
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerSeatPo actor = seat(roomId, request.seatId());
        if (actor.getUserId() == null || !actor.getUserId().equals(principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_ACTION_SEAT_FORBIDDEN");
        }
        return switch (request.actionType()) {
            case "PUBLIC_SPEECH" -> publicSpeech(room, actor, request, principal);
            case "PRIVATE_MESSAGE" -> privateMessage(room, actor, request, principal);
            case "NOMINATE" -> nominate(room, actor, request, principal);
            case "VOTE" -> vote(room, actor, request, principal);
            case "PASS" -> append(room, actor, request, principal, ClocktowerEventType.PLAYER_PASSED,
                    ClocktowerVisibility.PUBLIC, List.of(), Map.of("content", text(request.content())));
            default -> ClocktowerActionResponse.rejected("UNKNOWN_ACTION_TYPE");
        };
    }

    private ClocktowerActionResponse publicSpeech(ClocktowerRoomPo room, ClocktowerSeatPo actor,
                                                  ClocktowerActionRequest request, RbacPrincipal principal) {
        return append(room, actor, request, principal, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                ClocktowerVisibility.PUBLIC, List.of(), Map.of("content", text(request.content())));
    }

    private ClocktowerActionResponse privateMessage(ClocktowerRoomPo room, ClocktowerSeatPo actor,
                                                    ClocktowerActionRequest request, RbacPrincipal principal) {
        List<Long> targetSeatIds = request.targetSeatIds() == null ? List.of() : request.targetSeatIds();
        if (targetSeatIds.isEmpty()) {
            return reject(room, actor, principal, "PRIVATE_MESSAGE_TARGET_REQUIRED");
        }
        return append(room, actor, request, principal, ClocktowerEventType.PRIVATE_MESSAGE_SENT,
                ClocktowerVisibility.PRIVATE, targetSeatIds,
                Map.of("content", text(request.content()), "targetSeatIds", targetSeatIds));
    }

    private ClocktowerActionResponse nominate(ClocktowerRoomPo room, ClocktowerSeatPo actor,
                                              ClocktowerActionRequest request, RbacPrincipal principal) {
        if (room.getPhase() != ClocktowerPhase.DAY && room.getPhase() != ClocktowerPhase.NOMINATION) {
            return reject(room, actor, principal, "CLOCKTOWER_NOMINATION_PHASE_INVALID");
        }
        if (!canNominate(actor)) {
            return reject(room, actor, principal, "CLOCKTOWER_NOMINATOR_NOT_ALIVE");
        }
        Long targetSeatId = firstTarget(request);
        ClocktowerSeatPo target = seat(room.getId(), targetSeatId);
        if (!canNominate(target)) {
            return reject(room, actor, principal, "CLOCKTOWER_NOMINEE_NOT_ALIVE");
        }
        if (nominationRepository.findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(room.getId(), "OPEN").isPresent()) {
            return reject(room, actor, principal, "CLOCKTOWER_OPEN_NOMINATION_EXISTS");
        }
        List<ClocktowerNominationPo> today = nominationRepository
                .findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(room.getId(), room.getCurrentDayNo());
        if (today.stream().anyMatch(nomination -> nomination.getNominatorSeatId().equals(actor.getId()))) {
            return reject(room, actor, principal, "CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED_TODAY");
        }
        if (today.stream().anyMatch(nomination -> nomination.getNomineeSeatId().equals(target.getId()))) {
            return reject(room, actor, principal, "CLOCKTOWER_NOMINEE_ALREADY_NOMINATED_TODAY");
        }
        ClocktowerNominationPo nomination = new ClocktowerNominationPo();
        nomination.setRoomId(room.getId());
        nomination.setDayNo(room.getCurrentDayNo());
        nomination.setNominatorSeatId(actor.getId());
        nomination.setNomineeSeatId(target.getId());
        nomination.setStatus("OPEN");
        nominationRepository.save(nomination);
        room.setPhase(ClocktowerPhase.NOMINATION);
        roomRepository.save(room);
        return append(room, actor, request, principal, ClocktowerEventType.PLAYER_NOMINATED,
                ClocktowerVisibility.PUBLIC, List.of(),
                Map.of("nominationId", nomination.getId(), "targetSeatId", target.getId(), "content", text(request.content())));
    }

    private ClocktowerActionResponse vote(ClocktowerRoomPo room, ClocktowerSeatPo actor,
                                          ClocktowerActionRequest request, RbacPrincipal principal) {
        if (room.getPhase() != ClocktowerPhase.NOMINATION) {
            return reject(room, actor, principal, "CLOCKTOWER_VOTE_PHASE_INVALID");
        }
        ClocktowerNominationPo nomination = nominationRepository
                .findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(room.getId(), "OPEN")
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        if (voteRepository.findByNominationIdAndVoterSeatIdAndDeletedFalse(nomination.getId(), actor.getId()).isPresent()) {
            return reject(room, actor, principal, "CLOCKTOWER_VOTE_ALREADY_CAST");
        }
        boolean usedDeadVote = "DEAD".equals(actor.getLifeStatus()) || "DEAD".equals(actor.getPublicLifeStatus());
        if (usedDeadVote && (!actor.isHasDeadVote()
                || voteRepository.existsByRoomIdAndVoterSeatIdAndUsedDeadVoteTrueAndDeletedFalse(
                room.getId(), actor.getId()))) {
            return reject(room, actor, principal, "CLOCKTOWER_DEAD_VOTE_ALREADY_SPENT");
        }
        ClocktowerVotePo vote = new ClocktowerVotePo();
        vote.setRoomId(room.getId());
        vote.setNominationId(nomination.getId());
        vote.setVoterSeatId(actor.getId());
        vote.setVoteValue(booleanPayload(request, "vote", true));
        vote.setUsedDeadVote(usedDeadVote);
        voteRepository.save(vote);
        if (vote.isVoteValue()) {
            nomination.setVoteCount(nomination.getVoteCount() + 1);
            nominationRepository.save(nomination);
        }
        if (usedDeadVote) {
            actor.setHasDeadVote(false);
            seatRepository.save(actor);
        }
        ClocktowerActionResponse response = append(room, actor, request, principal, ClocktowerEventType.VOTE_CAST,
                ClocktowerVisibility.PUBLIC, List.of(),
                Map.of("nominationId", nomination.getId(), "voteValue", vote.isVoteValue(), "usedDeadVote", usedDeadVote));
        if (response.event() != null) {
            vote.setEventId(response.event().eventId());
            voteRepository.save(vote);
        }
        return response;
    }

    private ClocktowerActionResponse reject(ClocktowerRoomPo room, ClocktowerSeatPo actor, RbacPrincipal principal,
                                            String code) {
        eventService.append(new ClocktowerEventAppendRequest(room.getId(), ClocktowerEventType.ACTION_REJECTED,
                room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo(),
                principal == null ? null : principal.userId(), actor.getId(), null, ClocktowerVisibility.PRIVATE,
                List.of(actor.getId()), Map.of("rejectedCode", code)));
        return ClocktowerActionResponse.rejected(code);
    }

    private ClocktowerActionResponse append(ClocktowerRoomPo room, ClocktowerSeatPo actor, ClocktowerActionRequest request,
                                            RbacPrincipal principal, ClocktowerEventType eventType,
                                            ClocktowerVisibility visibility, List<Long> visibleSeatIds,
                                            Map<String, Object> payload) {
        ClocktowerEventResponse event = eventService.append(new ClocktowerEventAppendRequest(room.getId(), eventType,
                room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo(),
                principal == null ? null : principal.userId(), actor.getId(), firstTargetOrNull(request),
                visibility, visibleSeatIds, payload));
        return ClocktowerActionResponse.accepted(event);
    }

    private ClocktowerSeatPo seat(Long roomId, Long seatId) {
        if (seatId == null) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_REQUIRED");
        }
        return seatRepository.findByIdAndRoomIdAndDeletedFalse(seatId, roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
    }

    private static Long firstTarget(ClocktowerActionRequest request) {
        Long targetSeatId = firstTargetOrNull(request);
        if (targetSeatId == null) {
            throw new ClocktowerException("CLOCKTOWER_ACTION_TARGET_REQUIRED");
        }
        return targetSeatId;
    }

    private static Long firstTargetOrNull(ClocktowerActionRequest request) {
        return request.targetSeatIds() == null || request.targetSeatIds().isEmpty() ? null : request.targetSeatIds().getFirst();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean booleanPayload(ClocktowerActionRequest request, String key, boolean defaultValue) {
        if (request.payload() == null || !request.payload().containsKey(key)) {
            return defaultValue;
        }
        Object value = request.payload().get(key);
        return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }

    private static boolean canNominate(ClocktowerSeatPo seat) {
        return "ALIVE".equals(seat.getLifeStatus()) && "ALIVE".equals(seat.getPublicLifeStatus());
    }
}
