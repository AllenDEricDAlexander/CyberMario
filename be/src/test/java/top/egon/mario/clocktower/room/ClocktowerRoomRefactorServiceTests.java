package top.egon.mario.clocktower.room;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomBoardSwitchRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomInvitationCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomMemberActionRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatReleaseRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomInvitationResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerRoomRefactorServiceTests {

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerBoardService boardService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private RoomSpaceRepository roomSpaceRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private RoomInvitationRepository roomInvitationRepository;

    @Autowired
    private ClocktowerRoomProfileRepository profileRepository;

    @Autowired
    private ClocktowerRoomSeatRepository seatRepository;

    @Autowired
    private ImChannelRepository imChannelRepository;

    @Autowired
    private ImGroupRepository imGroupRepository;

    @Autowired
    private ImConversationRepository imConversationRepository;

    @Autowired
    private ImConversationMemberRepository imConversationMemberRepository;

    @Test
    void createRoomCreatesGenericRoomProfileSeatDraftAndRoomPublicConversation() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));

        RoomSpacePo roomSpace = roomSpaceRepository.findByIdAndDeletedFalse(room.roomId()).orElseThrow();
        assertThat(roomSpace.getContextType()).isEqualTo("CLOCKTOWER");
        assertThat(roomSpace.getContextId()).isEqualTo(room.roomId());
        assertThat(roomSpace.getOwnerUserId()).isEqualTo(1L);
        assertThat(roomSpace.getVisibility()).isEqualTo("PUBLIC");

        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(room.roomId()).orElseThrow();
        assertThat(profile.getScriptCode()).isEqualTo("TROUBLE_BREWING");
        assertThat(profile.getPlayerCount()).isEqualTo(5);
        assertThat(profile.getStatus()).isEqualTo("LOBBY");

        assertThat(seatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId()))
                .extracting(ClocktowerRoomSeatPo::getStatus, ClocktowerRoomSeatPo::getRoleCode)
                .containsExactly(
                        tuple("OPEN", "EMPATH"),
                        tuple("OPEN", "CHEF"),
                        tuple("OPEN", "MONK"),
                        tuple("OPEN", "POISONER"),
                        tuple("OPEN", "IMP")
                );
        assertThat(room.seats())
                .extracting(ClocktowerSeatResponse::hasDeadVote)
                .containsOnly(false);

        ImChannelPo channel = imChannelRepository
                .findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse("CLOCKTOWER", room.roomId(), "ROOM")
                .orElseThrow();
        ImGroupPo group = imGroupRepository.findByChannelIdAndGroupKeyAndDeletedFalse(channel.getId(), "PUBLIC")
                .orElseThrow();
        ImConversationPo conversation = imConversationRepository
                .findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                        ImSurfaceType.GROUP, group.getId(), ImConversationType.GROUP)
                .orElseThrow();
        assertThat(conversation.getContextType()).isEqualTo("CLOCKTOWER");
        assertThat(conversation.getContextId()).isEqualTo(room.roomId());
        assertThat(room.publicConversationId()).isEqualTo(conversation.getId());
    }

    @Test
    void enterRoomDefaultsToSpectator() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));

        roomService.enterRoom(room.roomId(), principal(2L, "luigi"));

        RoomMemberPo member = roomMemberRepository.findActiveByRoomIdAndUserId(room.roomId(), 2L).orElseThrow();
        assertThat(member.getMemberType()).isEqualTo("SPECTATOR");
        assertThat(member.getSeatNo()).isNull();
        assertThat(seatRepository.findByRoomIdAndUserId(room.roomId(), 2L)).isEmpty();
    }

    @Test
    void activeBanCannotViewPublicRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));

        roomService.kickMember(room.roomId(), 2L, new ClocktowerRoomMemberActionRequest(true, null, "blocked"),
                principal(1L, "mario"));

        assertThat(roomService.listVisibleRooms(principal(2L, "luigi")))
                .extracting(ClocktowerRoomResponse::roomId)
                .doesNotContain(room.roomId());
        assertThatThrownBy(() -> roomService.lobby(room.roomId(), principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_ROOM_FORBIDDEN");
    }

    @Test
    void createRoomRejectsApprovalRequiredUntilApprovalFlowExists() {
        assertThatThrownBy(() -> roomService.createRoom(createRequest("APPROVAL_REQUIRED"),
                principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEATING_POLICY_UNSUPPORTED");
    }

    @Test
    void createRoomRejectsUnknownSeatingPolicyBeforeGenericRoomCreation() {
        long roomCount = roomSpaceRepository.count();

        assertThatThrownBy(() -> roomService.createRoom(createRequest("RANDOM_POLICY"),
                principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEATING_POLICY_INVALID");

        assertThat(roomSpaceRepository.count()).isEqualTo(roomCount);
    }

    @Test
    void claimSeatDefaultsMissingSeatingPolicyToOpenSeating() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(null), principal(1L, "mario"));

        ClocktowerSeatResponse seat = roomService.claimSeat(room.roomId(), 2,
                new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi"));

        assertThat(seat.userId()).isEqualTo(2L);
        assertThat(seat.status()).isEqualTo("OCCUPIED");
        assertThat(seat.ready()).isTrue();
        assertThat(seatRepository.findByRoomIdAndUserId(room.roomId(), 2L)).isPresent();
        assertThat(roomInvitationRepository.findActiveTargetSeatReservations(room.roomId(), Instant.now()))
                .isEmpty();
    }

    @Test
    void claimSeatAddsClaimantToPublicConversationWithoutPriorEnter() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));

        assertThat(imConversationMemberRepository
                .existsByConversationIdAndUserIdAndStatusAndDeletedFalse(room.publicConversationId(), 2L, "ACTIVE"))
                .isFalse();

        roomService.claimSeat(room.roomId(), 2, new ClocktowerSeatClaimRequest("Luigi"),
                principal(2L, "luigi"));

        ClocktowerRoomSeatPo seat = seatRepository.findByRoomIdAndUserId(room.roomId(), 2L).orElseThrow();
        assertThat(seat.getMetadataJson()).contains("\"ready\":true");
        assertThat(imConversationMemberRepository
                .existsByConversationIdAndUserIdAndStatusAndDeletedFalse(room.publicConversationId(), 2L, "ACTIVE"))
                .isTrue();
    }

    @Test
    void claimSeatMovesExistingUserSeatWithoutUniqueConstraintCollision() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi"));

        ClocktowerSeatResponse moved = roomService.claimSeat(room.roomId(), 2,
                new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi"));

        assertThat(moved.userId()).isEqualTo(2L);
        assertThat(seatRepository.findByRoomIdAndSeatNo(room.roomId(), 1).orElseThrow().getUserId()).isNull();
        assertThat(seatRepository.findByRoomIdAndSeatNo(room.roomId(), 2).orElseThrow().getUserId()).isEqualTo(2L);
        assertThatCode(() -> seatRepository.flush()).doesNotThrowAnyException();
    }

    @Test
    void claimSeatClearsSoftDeletedUserSeatBeforeAssigningNewSeat() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi"));
        ClocktowerRoomSeatPo previousSeat = seatRepository.findByRoomIdAndSeatNo(room.roomId(), 1).orElseThrow();
        previousSeat.setDeleted(true);
        seatRepository.saveAndFlush(previousSeat);

        assertThatCode(() -> {
            roomService.claimSeat(room.roomId(), 2, new ClocktowerSeatClaimRequest("Luigi"),
                    principal(2L, "luigi"));
            seatRepository.flush();
        }).doesNotThrowAnyException();
        assertThat(seatRepository.findByRoomIdAndSeatNo(room.roomId(), 2).orElseThrow().getUserId()).isEqualTo(2L);
    }

    @Test
    void claimSeatRejectsStorytellerAsPlayer() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.claimSeat(room.roomId(), 2,
                new ClocktowerSeatClaimRequest("Mario"), principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_CANNOT_PLAY");
        assertThat(seatRepository.findByRoomIdAndUserId(room.roomId(), 1L)).isEmpty();
    }

    @Test
    void acceptSeatInvitationRejectsStorytellerAsPlayer() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));
        ClocktowerRoomInvitationResponse invitation = roomService.createInvitation(room.roomId(),
                new ClocktowerRoomInvitationCreateRequest(1L, "SEAT", 3,
                        Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.acceptInvitation(room.roomId(), invitation.invitationId(),
                principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_CANNOT_PLAY");
        assertThat(seatRepository.findByRoomIdAndUserId(room.roomId(), 1L)).isEmpty();
    }

    @Test
    void claimSeatRejectsNonLobbyProfileStatus() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(room.roomId()).orElseThrow();
        profile.setStatus("ACTIVE");
        profileRepository.saveAndFlush(profile);

        assertThatThrownBy(() -> roomService.claimSeat(room.roomId(), 2,
                new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_ROOM_NOT_LOBBY");
    }

    @Test
    void createInvitationMapsSeatReservationConflictToClocktowerError() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));
        roomService.createInvitation(room.roomId(), new ClocktowerRoomInvitationCreateRequest(2L, "SEAT", 2,
                Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.createInvitation(room.roomId(),
                new ClocktowerRoomInvitationCreateRequest(3L, "SEAT", 2,
                        Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_RESERVED");
    }

    @Test
    void acceptSeatInvitationAssignsSeatAndClearsReservationConflict() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));
        ClocktowerRoomInvitationResponse invitation = roomService.createInvitation(room.roomId(),
                new ClocktowerRoomInvitationCreateRequest(2L, "SEAT", 3,
                        Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario"));

        ClocktowerRoomInvitationResponse accepted = roomService.acceptInvitation(
                room.roomId(), invitation.invitationId(), principal(2L, "luigi"));

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        ClocktowerRoomSeatPo seat = seatRepository.findByRoomIdAndSeatNo(room.roomId(), 3).orElseThrow();
        assertThat(seat.getUserId()).isEqualTo(2L);
        assertThat(seat.getStatus()).isEqualTo("OCCUPIED");
        assertThat(roomInvitationRepository.findActiveTargetSeatReservations(room.roomId(), Instant.now()))
                .extracting(RoomInvitationPo::getTargetSeatNo)
                .doesNotContain(3);
    }

    @Test
    void claimSeatRejectsInviteOnlyWithoutSeatInvitation() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.claimSeat(room.roomId(), 2,
                new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_INVITATION_REQUIRED");
    }

    @Test
    void acceptSeatInvitationAddsInviteeToPublicConversationWithoutPriorEnter() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));
        ClocktowerRoomInvitationResponse invitation = roomService.createInvitation(room.roomId(),
                new ClocktowerRoomInvitationCreateRequest(2L, "SEAT", 3,
                        Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario"));

        assertThat(imConversationMemberRepository
                .existsByConversationIdAndUserIdAndStatusAndDeletedFalse(room.publicConversationId(), 2L, "ACTIVE"))
                .isFalse();

        roomService.acceptInvitation(room.roomId(), invitation.invitationId(), principal(2L, "luigi"));

        assertThat(imConversationMemberRepository
                .existsByConversationIdAndUserIdAndStatusAndDeletedFalse(room.publicConversationId(), 2L, "ACTIVE"))
                .isTrue();
    }

    @Test
    void switchBoardRejectsSmallerPlayerCountWhenSeatsReserved() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("INVITE_ONLY"), principal(1L, "mario"));
        roomService.createInvitation(room.roomId(), new ClocktowerRoomInvitationCreateRequest(2L, "SEAT", 4,
                Instant.now().plus(Duration.ofHours(1))), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.switchBoard(room.roomId(), new ClocktowerRoomBoardSwitchRequest(
                ClocktowerScriptCode.TROUBLE_BREWING, 3, null, null, List.of()), principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_RESERVED");
    }

    @Test
    void switchBoardRejectsSmallerPlayerCountWhenSeatsOccupied() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"),
                principal(1L, "mario"));
        roomService.claimSeat(room.roomId(), 4, new ClocktowerSeatClaimRequest("Peach"), principal(2L, "peach"));

        assertThatThrownBy(() -> roomService.switchBoard(room.roomId(), new ClocktowerRoomBoardSwitchRequest(
                ClocktowerScriptCode.TROUBLE_BREWING, 3, null, null, List.of()), principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_OCCUPIED");
    }

    @Test
    void switchBoardUsesCurrentScriptWhenSavedBoardRequestOmitsScript() {
        RbacPrincipal owner = principal(1L, "mario");
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), owner);
        ClocktowerBoardConfigResponse board = boardService.save(new ClocktowerBoardSaveRequest(
                ClocktowerScriptCode.TROUBLE_BREWING, 5, 0, 0, 0, true, "saved-board",
                roleCodes(), null), owner);

        ClocktowerRoomResponse switched = roomService.switchBoard(room.roomId(),
                new ClocktowerRoomBoardSwitchRequest(null, 5, board.boardId(), null, null), owner);

        assertThat(switched.scriptCode()).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(switched.playerCount()).isEqualTo(5);
    }

    @Test
    void releaseSeatLeavesUserAsSpectatorAndClearsReady() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), principal(1L, "mario"));
        roomService.claimSeat(room.roomId(), 2, new ClocktowerSeatClaimRequest("Luigi"), principal(2L, "luigi"));
        ClocktowerRoomSeatPo claimed = seatRepository.findByRoomIdAndUserId(room.roomId(), 2L).orElseThrow();
        claimed.setMetadataJson("{\"ready\":true}");
        seatRepository.save(claimed);

        ClocktowerSeatResponse released = roomService.releaseSeat(room.roomId(), 2,
                new ClocktowerSeatReleaseRequest(null), principal(2L, "luigi"));

        assertThat(released.userId()).isNull();
        assertThat(released.ready()).isFalse();
        RoomMemberPo member = roomMemberRepository.findActiveByRoomIdAndUserId(room.roomId(), 2L).orElseThrow();
        assertThat(member.getMemberType()).isEqualTo("SPECTATOR");
        assertThat(member.getSeatNo()).isNull();
        ClocktowerRoomSeatPo seat = seatRepository.findByRoomIdAndSeatNo(room.roomId(), 2).orElseThrow();
        assertThat(seat.getMetadataJson()).contains("\"ready\":false");
    }

    @Test
    void lobbyResponseExposesCurrentGameIdAfterGameStarts() {
        RbacPrincipal owner = principal(1L, "mario");
        ClocktowerRoomResponse room = readyRoom(owner);

        ClocktowerGameResponse game = gameService.startGame(room.roomId(), owner);

        assertThat(roomService.lobby(room.roomId(), owner).currentGameId()).isEqualTo(game.gameId());
    }

    private ClocktowerRoomResponse readyRoom(RbacPrincipal owner) {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING"), owner);
        for (int seatNo = 1; seatNo <= roleCodes().size(); seatNo++) {
            roomService.claimSeat(room.roomId(), seatNo, new ClocktowerSeatClaimRequest("Player " + seatNo),
                    principal(10L + seatNo, "player" + seatNo));
        }
        return room;
    }

    private static ClocktowerRoomCreateRequest createRequest(String seatingPolicy) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                null,
                null,
                roleCodes(),
                "HUMAN",
                true,
                true,
                0,
                "PUBLIC",
                seatingPolicy
        );
    }

    private static List<String> roleCodes() {
        return List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
