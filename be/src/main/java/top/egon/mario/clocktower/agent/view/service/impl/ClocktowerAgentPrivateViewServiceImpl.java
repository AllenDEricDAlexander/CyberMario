package top.egon.mario.clocktower.agent.view.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;
import top.egon.mario.clocktower.agent.view.dto.AgentVisibleEventView;
import top.egon.mario.clocktower.agent.view.service.ClocktowerAgentPrivateViewService;
import top.egon.mario.clocktower.agent.view.service.ClocktowerRoleVisionPolicy;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameVoteRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentPrivateViewServiceImpl implements ClocktowerAgentPrivateViewService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerAgentMemoryRepository memoryRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameVoteRepository voteRepository;
    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ClocktowerGamePublicMicSessionRepository micSessionRepository;
    private final ClocktowerPublicMicService publicMicService;
    private final ClocktowerRoleSkillRegistry roleSkillRegistry;
    private final ClocktowerRoleVisionPolicy roleVisionPolicy;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AgentPrivateView build(Long gameId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        ClocktowerGameSeatPo mySeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                        instance.getGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_SEAT_INVALID"));
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId);
        List<AgentVisibleEventView> events = visibleEvents(gameId, mySeat.getId());

        return new AgentPrivateView(gameId, instance.getId(), mySeat.getId(), mySeat.getSeatNo(),
                game.getPhase(), game.getDayNo(), game.getNightNo(), mySeat.getRoleCode(),
                mySeat.getRoleCode(), mySeat.getAlignment(), mySeat.getRoleType(), mySeat.getLifeStatus(),
                mySeat.getPublicLifeStatus(), mySeat.isHasDeadVote(), publicSeats(seats),
                grimoire(seats, mySeat), events, privateInfos(events), memories(gameId, instance.getId()),
                legalIntents(game, mySeat, seats), roleSpecificContext(seats, mySeat));
    }

    private List<AgentPublicSeatView> publicSeats(List<ClocktowerGameSeatPo> seats) {
        return seats.stream()
                .map(seat -> seatView(seat, false))
                .toList();
    }

    private List<AgentPublicSeatView> grimoire(List<ClocktowerGameSeatPo> seats, ClocktowerGameSeatPo mySeat) {
        if (!roleVisionPolicy.canSeeGrimoire(mySeat.getRoleCode())) {
            return List.of();
        }
        return seats.stream()
                .map(seat -> seatView(seat, true))
                .toList();
    }

    private AgentPublicSeatView seatView(ClocktowerGameSeatPo seat, boolean revealRole) {
        return new AgentPublicSeatView(seat.getId(), seat.getSeatNo(), seat.getDisplayName(),
                revealRole ? seat.getRoleCode() : null, revealRole ? seat.getRoleType() : null,
                revealRole ? seat.getAlignment() : null, revealRole ? seat.getLifeStatus() : null,
                seat.getPublicLifeStatus(), seat.isHasDeadVote(), seat.isTraveler(), seat.getActorType(),
                "AGENT".equals(seat.getActorType()), seat.getStatus());
    }

    private List<AgentVisibleEventView> visibleEvents(Long gameId, Long myGameSeatId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .filter(event -> visibleToAgent(event, myGameSeatId))
                .map(this::eventView)
                .toList();
    }

    private boolean visibleToAgent(ClocktowerGameEventPo event, Long myGameSeatId) {
        if ("PUBLIC".equals(event.getVisibility())) {
            return true;
        }
        if (!"PRIVATE".equals(event.getVisibility())) {
            return false;
        }
        return readLongList(event.getVisibleGameSeatIdsJson()).contains(myGameSeatId);
    }

    private AgentVisibleEventView eventView(ClocktowerGameEventPo event) {
        return new AgentVisibleEventView(event.getId(), event.getEventSeq(), event.getEventType(),
                event.getPhase(), event.getDayNo(), event.getNightNo(), event.getActorGameSeatId(),
                event.getTargetGameSeatId(), event.getVisibility(), readLongList(event.getVisibleGameSeatIdsJson()),
                readMap(event.getPayloadJson()), event.getOccurredAt());
    }

    private List<AgentPrivateInfoView> privateInfos(List<AgentVisibleEventView> events) {
        return events.stream()
                .filter(event -> "PRIVATE_INFO_RECEIVED".equals(event.eventType()))
                .map(event -> new AgentPrivateInfoView(event.eventId(), event.eventSeq(),
                        stringValue(event.payload().get("roleCode")),
                        stringValue(event.payload().get("taskType")), event.payload(), event.occurredAt()))
                .toList();
    }

    private List<AgentMemoryView> memories(Long gameId, Long agentInstanceId) {
        return memoryRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                        gameId, agentInstanceId)
                .stream()
                .map(this::memoryView)
                .toList();
    }

    private AgentMemoryView memoryView(ClocktowerAgentMemoryPo memory) {
        return new AgentMemoryView(memory.getId(), memory.getSourceEventId(), memory.getSourceEventSeq(),
                memory.getMemoryType(), memory.getSubjectGameSeatId(), readMap(memory.getContentJson()),
                memory.getConfidence(), memory.getDayNo(), memory.getNightNo(), memory.getCreatedAt());
    }

    private List<AgentLegalIntentView> legalIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat,
                                                    List<ClocktowerGameSeatPo> seats) {
        List<AgentLegalIntentView> intents = new ArrayList<>();
        appendMicIntents(game, mySeat, intents);
        appendNominationIntents(game, mySeat, seats, intents);
        appendVoteIntents(game, mySeat, intents);
        appendNightChoiceIntents(game, mySeat, seats, intents);
        return intents;
    }

    private void appendMicIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat,
                                  List<AgentLegalIntentView> intents) {
        if (publicMicService.canSpeak(game.getId(), mySeat.getId())) {
            intents.add(new AgentLegalIntentView("PUBLIC_SPEECH", null, null, null, Map.of()));
            intents.add(new AgentLegalIntentView("PASS", null, null, null, Map.of("passType", "MIC_TURN")));
        }
        micSessionRepository.findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .filter(session -> "GRAB_MIC".equals(session.getStatus()))
                .filter(session -> session.getCurrentHolderGameSeatId() == null)
                .filter(session -> session.getGrabEndsAt() != null && session.getGrabEndsAt().isAfter(Instant.now()))
                .filter(session -> "ACTIVE".equals(mySeat.getStatus()))
                .ifPresent(session -> intents.add(new AgentLegalIntentView("GRAB_MIC", null, null, null,
                        Map.of("sessionId", session.getId(), "grabEndsAt", session.getGrabEndsAt()))));
    }

    private void appendNominationIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat,
                                         List<ClocktowerGameSeatPo> seats,
                                         List<AgentLegalIntentView> intents) {
        if (!"DAY".equals(game.getPhase()) && !"NOMINATION".equals(game.getPhase())) {
            return;
        }
        if ("DEAD".equals(mySeat.getLifeStatus())) {
            return;
        }
        if (nominationRepository.findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(game.getId(), "OPEN")
                .isPresent()) {
            return;
        }
        if (activeMicHolder(game)) {
            return;
        }
        if (nominationRepository.existsByGameIdAndDayNoAndNominatorGameSeatIdAndDeletedFalse(
                game.getId(), game.getDayNo(), mySeat.getId())) {
            return;
        }
        List<Long> targets = seats.stream()
                .filter(seat -> "ACTIVE".equals(seat.getStatus()))
                .filter(seat -> !"DEAD".equals(seat.getLifeStatus()))
                .filter(seat -> !Objects.equals(seat.getId(), mySeat.getId()))
                .filter(seat -> !nominationRepository.existsByGameIdAndDayNoAndNomineeGameSeatIdAndDeletedFalse(
                        game.getId(), game.getDayNo(), seat.getId()))
                .map(ClocktowerGameSeatPo::getId)
                .toList();
        if (!targets.isEmpty()) {
            intents.add(new AgentLegalIntentView("NOMINATE", null, null, null,
                    Map.of("eligibleTargetGameSeatIds", targets)));
        }
    }

    private boolean activeMicHolder(ClocktowerGamePo game) {
        return micSessionRepository.findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .filter(session -> !"CLOSED".equals(session.getStatus()))
                .map(ClocktowerGamePublicMicSessionPo::getCurrentHolderGameSeatId)
                .filter(holder -> holder != null)
                .isPresent();
    }

    private void appendVoteIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat,
                                   List<AgentLegalIntentView> intents) {
        nominationRepository.findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(game.getId(), "OPEN")
                .filter(nomination -> !voteRepository.existsByNominationIdAndVoterGameSeatIdAndDeletedFalse(
                        nomination.getId(), mySeat.getId()))
                .ifPresent(nomination -> {
                    intents.add(voteIntent(nomination, true));
                    intents.add(voteIntent(nomination, false));
                });
    }

    private void appendNightChoiceIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat,
                                          List<ClocktowerGameSeatPo> seats,
                                          List<AgentLegalIntentView> intents) {
        List<top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo> currentNightTasks =
                nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                        game.getId(), game.getNightNo());
        nightTaskRepository.findByGameIdAndNightNoAndActorGameSeatIdAndDeletedFalseOrderBySortOrderAscIdAsc(
                        game.getId(), game.getNightNo(), mySeat.getId())
                .stream()
                .filter(task -> "PENDING".equals(task.getStatus()))
                .forEach(task -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    if (task.getTaskType() != null) {
                        payload.put("taskType", task.getTaskType());
                    }
                    if (task.getRoleCode() != null) {
                        payload.put("roleCode", task.getRoleCode());
                    }
                    payload.put("legalTargetGameSeatIds", legalNightTargetIds(game, task, mySeat, seats,
                            currentNightTasks));
                    intents.add(new AgentLegalIntentView("NIGHT_CHOICE", task.getId(), null, null, payload));
                });
    }

    private List<Long> legalNightTargetIds(ClocktowerGamePo game,
                                           top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo task,
                                           ClocktowerGameSeatPo mySeat,
                                           List<ClocktowerGameSeatPo> seats,
                                           List<top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo>
                                                   currentNightTasks) {
        return roleSkillRegistry.find(task.getRoleCode())
                .map(skill -> skill.legalTargets(new NightTaskContext(game, task, mySeat, seats, currentNightTasks,
                                readMap(task.getMetadataJson())))
                        .stream()
                        .filter(AvailableTargetSpec::selectable)
                        .map(AvailableTargetSpec::gameSeatId)
                        .toList())
                .orElse(List.of());
    }

    private AgentLegalIntentView voteIntent(ClocktowerGameNominationPo nomination, boolean voteValue) {
        return new AgentLegalIntentView("VOTE", null, nomination.getId(), voteValue,
                Map.of("nomineeGameSeatId", nomination.getNomineeGameSeatId()));
    }

    private Map<String, Object> roleSpecificContext(List<ClocktowerGameSeatPo> seats, ClocktowerGameSeatPo mySeat) {
        if (!"EVIL".equals(mySeat.getAlignment())) {
            return Map.of();
        }
        List<Map<String, Object>> evilTeam = seats.stream()
                .filter(seat -> "EVIL".equals(seat.getAlignment()))
                .map(seat -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("gameSeatId", seat.getId());
                    entry.put("seatNo", seat.getSeatNo());
                    entry.put("displayName", seat.getDisplayName());
                    entry.put("roleCode", seat.getRoleCode());
                    entry.put("roleType", seat.getRoleType());
                    entry.put("isDemon", "DEMON".equals(seat.getRoleType()));
                    entry.put("isMinion", "MINION".equals(seat.getRoleType()));
                    return entry;
                })
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("evilTeam", evilTeam);
        evilTeam.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("isDemon")))
                .map(entry -> entry.get("gameSeatId"))
                .findFirst()
                .ifPresent(demonGameSeatId -> context.put("demonGameSeatId", demonGameSeatId));
        return context;
    }

    private List<Long> readLongList(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, LONG_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_VIEW_JSON_INVALID");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_VIEW_JSON_INVALID");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
