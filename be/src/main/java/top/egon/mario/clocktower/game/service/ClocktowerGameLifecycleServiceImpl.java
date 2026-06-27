package top.egon.mario.clocktower.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameConversationResponse;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomMutationPolicy;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.im.legacy.LegacyImFacade;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerGameLifecycleServiceImpl implements ClocktowerGameLifecycleService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOBBY = "LOBBY";
    private static final String STATUS_IN_GAME = "IN_GAME";
    private static final String STATUS_DISBANDED = "DISBANDED";
    private static final String SEAT_STATUS_OCCUPIED = "OCCUPIED";
    private static final String GAME_STATUS_DRAFT = "DRAFT";
    private static final String GAME_STATUS_RUNNING = "RUNNING";
    private static final String GAME_STATUS_ENDED = "ENDED";
    private static final String GAME_STATUS_ABORTED = "ABORTED";
    private static final String GAME_STATUS_ARCHIVED = "ARCHIVED";
    private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
    private static final String PHASE_ENDED = "ENDED";
    private static final String ACTION_START = "START";
    private static final String ACTION_END = "END";
    private static final String ACTION_ABORT = "ABORT";
    private static final String ACTION_ARCHIVE = "ARCHIVE";
    private static final String IM_CHANNEL_GAME = "GAME";
    private static final String IM_SCOPE_GAME = "GAME";
    private static final String IM_GROUP_PUBLIC = "PUBLIC";
    private static final String IM_GROUP_PRIVATE = "PRIVATE";
    private static final String IM_GROUP_SPECTATOR = "SPECTATOR";
    private static final String IM_GROUP_SYSTEM = "SYSTEM";
    private static final String IM_CONVERSATION_PRIVATE_CONTAINER = "PRIVATE_CONTAINER";

    private final RoomSpaceRepository roomSpaceRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final ClocktowerRoomSeatRepository roomSeatRepository;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerBoardService boardService;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final LegacyImFacade imFacade;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerGameResponse startGame(Long roomId, RbacPrincipal principal) {
        Instant now = Instant.now();
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireOwner(room, principal);
        requireStartable(profile);

        List<ClocktowerRoomSeatPo> seats = requiredSeats(roomId, profile);
        rejectActiveSeatReservations(roomId, now);
        Map<String, ClocktowerRolePo> rolesByCode = rolesByCode(seats);
        validateStartSeats(profile, seats, rolesByCode);
        validateBoardDraft(profile, seats);

        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(roomId);
        game.setGameNo(nextGameNo(roomId));
        game.setScriptCode(profile.getScriptCode());
        game.setStatus(transition(GAME_STATUS_DRAFT, ACTION_START));
        game.setPhase(PHASE_FIRST_NIGHT);
        game.setNightNo(1);
        game.setStartedAt(now);
        game.setLastActiveAt(now);
        game.setBoardSnapshotJson(boardSnapshot(profile, seats));
        ClocktowerGamePo savedGame = gameRepository.saveAndFlush(game);

        List<ClocktowerGameSeatPo> gameSeats = seats.stream()
                .map(seat -> gameSeat(savedGame.getId(), seat, rolesByCode.get(seat.getRoleCode())))
                .toList();
        gameSeatRepository.saveAll(gameSeats);

        profile.setCurrentGameId(savedGame.getId());
        profile.setStatus(STATUS_IN_GAME);
        profile.setLastActiveAt(now);
        room.setLastActiveAt(now);

        appendGameEvent(savedGame, "GAME_STARTED", now, Map.of("roomId", roomId, "gameNo", savedGame.getGameNo()));
        List<ClocktowerGameConversationResponse> conversations = activateGameConversations(savedGame.getId(),
                seats.stream().map(ClocktowerRoomSeatPo::getUserId).toList());
        return ClocktowerGameResponse.from(savedGame, conversations);
    }

    @Override
    @Transactional
    public ClocktowerGameResponse endGame(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo existing = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        RoomSpacePo room = lockedRoom(existing.getRoomId());
        ClocktowerRoomProfilePo profile = lockedProfile(existing.getRoomId());
        accessPolicy.requireOwner(room, principal);
        ClocktowerGamePo game = lockedGame(gameId);
        if (!GAME_STATUS_RUNNING.equals(game.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_TRANSITION_INVALID");
        }
        Instant now = Instant.now();
        game.setStatus(transition(game.getStatus(), ACTION_END));
        game.setPhase(PHASE_ENDED);
        game.setEndedAt(now);
        game.setLastActiveAt(now);
        profile.setCurrentGameId(null);
        profile.setStatus(STATUS_LOBBY);
        profile.setLastActiveAt(now);
        room.setStatus(STATUS_ACTIVE);
        room.setLastActiveAt(now);
        appendGameEvent(game, "GAME_ENDED", now, Map.of("roomId", game.getRoomId(), "gameNo", game.getGameNo()));
        return ClocktowerGameResponse.from(game, List.of());
    }

    @Override
    @Transactional
    public ClocktowerGameResponse abortGame(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo existing = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        RoomSpacePo room = lockedRoom(existing.getRoomId());
        ClocktowerRoomProfilePo profile = lockedProfile(existing.getRoomId());
        accessPolicy.requireOwner(room, principal);
        ClocktowerGamePo game = lockedGame(gameId);
        abortRunningGame(game, room, profile, Instant.now(), false);
        return ClocktowerGameResponse.from(game, List.of());
    }

    @Override
    @Transactional
    public boolean abortTimedOutRoom(Long roomId, Duration timeout, Instant now, RbacPrincipal principal) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new ClocktowerException("CLOCKTOWER_GAME_TIMEOUT_INVALID");
        }
        Instant checkedNow = now == null ? Instant.now() : now;
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireOwner(room, principal);
        if (!STATUS_IN_GAME.equals(profile.getStatus()) || profile.getCurrentGameId() == null) {
            return false;
        }
        ClocktowerGamePo game = lockedGame(profile.getCurrentGameId());
        Instant latestActiveAt = latestActiveAt(game, profile, room);
        if (!GAME_STATUS_RUNNING.equals(game.getStatus()) || latestActiveAt == null
                || !latestActiveAt.isBefore(checkedNow.minus(timeout))) {
            return false;
        }
        abortRunningGame(game, room, profile, checkedNow, true);
        return true;
    }

    private Instant latestActiveAt(ClocktowerGamePo game, ClocktowerRoomProfilePo profile, RoomSpacePo room) {
        Instant latest = game.getLastActiveAt();
        latest = later(latest, profile.getLastActiveAt());
        return later(latest, room.getLastActiveAt());
    }

    private Instant later(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }

    private void abortRunningGame(ClocktowerGamePo game, RoomSpacePo room, ClocktowerRoomProfilePo profile,
                                  Instant now, boolean disbandRoom) {
        if (!GAME_STATUS_RUNNING.equals(game.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_TRANSITION_INVALID");
        }
        game.setStatus(transition(game.getStatus(), ACTION_ABORT));
        game.setPhase(PHASE_ENDED);
        game.setEndedAt(now);
        game.setLastActiveAt(now);
        profile.setCurrentGameId(null);
        profile.setStatus(disbandRoom ? STATUS_DISBANDED : STATUS_LOBBY);
        profile.setLastActiveAt(now);
        room.setStatus(disbandRoom ? STATUS_DISBANDED : STATUS_ACTIVE);
        room.setLastActiveAt(now);
        appendGameEvent(game, "GAME_ABORTED", now, Map.of("roomId", game.getRoomId(), "gameNo", game.getGameNo()));
    }

    private void requireStartable(ClocktowerRoomProfilePo profile) {
        if (profile.getCurrentGameId() != null || STATUS_IN_GAME.equals(profile.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ALREADY_RUNNING");
        }
        if (!STATUS_LOBBY.equals(profile.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_NOT_LOBBY");
        }
    }

    private List<ClocktowerRoomSeatPo> requiredSeats(Long roomId, ClocktowerRoomProfilePo profile) {
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId).stream()
                .filter(seat -> seat.getSeatNo() <= profile.getPlayerCount())
                .sorted(Comparator.comparingInt(ClocktowerRoomSeatPo::getSeatNo))
                .toList();
        if (seats.size() != profile.getPlayerCount()) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_COUNT_INVALID");
        }
        return seats;
    }

    private void rejectActiveSeatReservations(Long roomId, Instant now) {
        List<RoomInvitationPo> reservations = roomInvitationRepository.findActiveTargetSeatReservations(roomId, now);
        if (!reservations.isEmpty()) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_RESERVED");
        }
    }

    private Map<String, ClocktowerRolePo> rolesByCode(List<ClocktowerRoomSeatPo> seats) {
        List<String> roleCodes = seats.stream()
                .map(ClocktowerRoomSeatPo::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return roleRepository.findByRoleCodeInAndDeletedFalse(roleCodes).stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, Function.identity(), (left, right) -> left));
    }

    private void validateStartSeats(ClocktowerRoomProfilePo profile, List<ClocktowerRoomSeatPo> seats,
                                    Map<String, ClocktowerRolePo> rolesByCode) {
        for (ClocktowerRoomSeatPo seat : seats) {
            Map<String, Object> metadata = metadata(seat.getMetadataJson());
            if (seat.getUserId() == null || !SEAT_STATUS_OCCUPIED.equals(seat.getStatus())
                    || !realUser(metadata)) {
                throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INVALID");
            }
            if (Objects.equals(profile.getStorytellerUserId(), seat.getUserId())) {
                throw new ClocktowerException("CLOCKTOWER_STORYTELLER_CANNOT_PLAY");
            }
            if (!ready(metadata)) {
                throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_READY");
            }
            if (!StringUtils.hasText(seat.getRoleCode())) {
                throw new ClocktowerException("CLOCKTOWER_GAME_ROLE_REQUIRED");
            }
            ClocktowerRolePo role = rolesByCode.get(seat.getRoleCode());
            if (role == null || !profile.getScriptCode().equals(role.getScriptCode().name())) {
                throw new ClocktowerException("CLOCKTOWER_ASSIGNMENT_INVALID");
            }
        }
    }

    private void validateBoardDraft(ClocktowerRoomProfilePo profile, List<ClocktowerRoomSeatPo> seats) {
        BoardValidationResponse validation = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.valueOf(profile.getScriptCode()),
                profile.getPlayerCount(),
                seats.stream().map(ClocktowerRoomSeatPo::getRoleCode).toList()));
        if (!validation.valid()) {
            throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
        }
    }

    private boolean realUser(Map<String, Object> metadata) {
        if (Boolean.TRUE.equals(metadata.get("fake")) || Boolean.TRUE.equals(metadata.get("agent"))) {
            return false;
        }
        Object playerType = metadata.get("playerType");
        return playerType == null || "HUMAN".equalsIgnoreCase(playerType.toString());
    }

    private boolean ready(Map<String, Object> metadata) {
        Object value = metadata.get("ready");
        return value instanceof Boolean ready && ready;
    }

    private int nextGameNo(Long roomId) {
        return gameRepository.findTopByRoomIdAndDeletedFalseOrderByGameNoDesc(roomId)
                .map(game -> game.getGameNo() + 1)
                .orElse(1);
    }

    private ClocktowerGameSeatPo gameSeat(Long gameId, ClocktowerRoomSeatPo roomSeat, ClocktowerRolePo role) {
        ClocktowerGameSeatPo seat = new ClocktowerGameSeatPo();
        seat.setGameId(gameId);
        seat.setRoomSeatId(roomSeat.getId());
        seat.setSeatNo(roomSeat.getSeatNo());
        seat.setUserId(roomSeat.getUserId());
        seat.setDisplayName(roomSeat.getDisplayName());
        seat.setRoleCode(role.getRoleCode());
        seat.setRoleType(role.getRoleType().name());
        seat.setAlignment(role.getAlignment().name());
        seat.setTraveler(roomSeat.isTraveler());
        seat.setStatus(STATUS_ACTIVE);
        seat.setMetadataJson(roomSeat.getMetadataJson());
        return seat;
    }

    private String boardSnapshot(ClocktowerRoomProfilePo profile, List<ClocktowerRoomSeatPo> seats) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scriptCode", profile.getScriptCode());
        snapshot.put("playerCount", profile.getPlayerCount());
        snapshot.put("seats", seats.stream()
                .map(seat -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("roomSeatId", seat.getId());
                    value.put("seatNo", seat.getSeatNo());
                    value.put("userId", seat.getUserId());
                    value.put("displayName", seat.getDisplayName());
                    value.put("roleCode", seat.getRoleCode());
                    value.put("traveler", seat.isTraveler());
                    return value;
                })
                .toList());
        return writeJson(snapshot);
    }

    private void appendGameEvent(ClocktowerGamePo game, String eventType, Instant occurredAt,
                                 Map<String, Object> payload) {
        ClocktowerGameEventPo event = new ClocktowerGameEventPo();
        event.setGameId(game.getId());
        event.setEventSeq(nextEventSeq(game.getId()));
        event.setEventType(eventType);
        event.setPhase(game.getPhase());
        event.setDayNo(game.getDayNo());
        event.setNightNo(game.getNightNo());
        event.setVisibility("PUBLIC");
        event.setVisibleGameSeatIdsJson("[]");
        event.setPayloadJson(writeJson(payload));
        event.setStatus("VISIBLE");
        event.setOccurredAt(occurredAt);
        gameEventRepository.save(event);
    }

    private long nextEventSeq(Long gameId) {
        return gameEventRepository.findTopByGameIdAndDeletedFalseOrderByEventSeqDesc(gameId)
                .map(event -> event.getEventSeq() + 1)
                .orElse(1L);
    }

    private List<ClocktowerGameConversationResponse> activateGameConversations(Long gameId,
                                                                               Collection<Long> playerUserIds) {
        ImChannelPo channel = imFacade.ensureChannel(ClocktowerRoomMutationPolicy.CONTEXT_TYPE, gameId,
                IM_CHANNEL_GAME);
        return List.of(
                ensureConversation(channel, gameId, IM_GROUP_PUBLIC, IM_GROUP_PUBLIC, playerUserIds),
                ensureConversation(channel, gameId, IM_GROUP_PRIVATE, IM_CONVERSATION_PRIVATE_CONTAINER, List.of()),
                ensureConversation(channel, gameId, IM_GROUP_SPECTATOR, IM_GROUP_SPECTATOR, List.of()),
                ensureConversation(channel, gameId, IM_GROUP_SYSTEM, IM_GROUP_SYSTEM, List.of())
        );
    }

    private ClocktowerGameConversationResponse ensureConversation(ImChannelPo channel, Long gameId,
                                                                  String groupKey, String conversationType,
                                                                  Collection<Long> participantUserIds) {
        ImGroupPo group = imFacade.ensureGroup(channel.getId(), groupKey);
        ImConversationPo conversation = imFacade.ensureConversation(group.getId(), IM_SCOPE_GAME, gameId,
                conversationType, participantUserIds);
        return new ClocktowerGameConversationResponse(groupKey, conversationType, conversation.getId());
    }

    private RoomSpacePo lockedRoom(Long roomId) {
        if (roomId == null) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_ID_REQUIRED");
        }
        return roomSpaceRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }

    private ClocktowerRoomProfilePo lockedProfile(Long roomId) {
        return profileRepository.findLockedByRoomId(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_PROFILE_NOT_FOUND"));
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private String transition(String status, String action) {
        if (GAME_STATUS_DRAFT.equals(status) && ACTION_START.equals(action)) {
            return GAME_STATUS_RUNNING;
        }
        if (GAME_STATUS_RUNNING.equals(status) && ACTION_END.equals(action)) {
            return GAME_STATUS_ENDED;
        }
        if (GAME_STATUS_RUNNING.equals(status) && ACTION_ABORT.equals(action)) {
            return GAME_STATUS_ABORTED;
        }
        if ((GAME_STATUS_ENDED.equals(status) || GAME_STATUS_ABORTED.equals(status))
                && ACTION_ARCHIVE.equals(action)) {
            return GAME_STATUS_ARCHIVED;
        }
        throw new ClocktowerException("CLOCKTOWER_GAME_TRANSITION_INVALID");
    }

    private Map<String, Object> metadata(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }
}
