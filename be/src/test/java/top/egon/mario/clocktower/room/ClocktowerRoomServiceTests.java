package top.egon.mario.clocktower.room;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRoleTypeCountResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerRoomServiceTests {

    private final ClocktowerRoomService roomService = ClocktowerRoomTestFactory.service();

    @Test
    void createRoomCreatesLobbySeatsAndRoomCreatedEvent() {
        ClocktowerRoomCreateRequest request = createFivePlayerRequest();

        ClocktowerRoomResponse room = roomService.create(request, principal(1L, "mario"));

        assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.phase()).isEqualTo(ClocktowerPhase.LOBBY);
        assertThat(room.seats()).hasSize(5);
        assertThat(room.roomCode()).hasSize(6);
    }

    @Test
    void createRoomAllowsLobbyWithoutPresetRoles() {
        ClocktowerRoomService service = ClocktowerRoomTestFactory.context(new RejectEmptyBoardService()).roomService();
        ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of(), "HUMAN", false, true, 0);

        ClocktowerRoomResponse room = service.create(request, principal(1L, "mario"));

        assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.seats()).hasSize(5);
    }

    @Test
    void createRoomUsesValidSavedBoardRoles() {
        SavedBoardService boardService = new SavedBoardService(SavedBoardMode.VALID);
        ClocktowerRoomService service = ClocktowerRoomTestFactory.context(boardService).roomService();
        ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, 42L, null,
                List.of(), "HUMAN", false, true, 0);

        ClocktowerRoomResponse room = service.create(request, principal(1L, "mario"));

        assertThat(boardService.usableBoardCalled()).isTrue();
        assertThat(boardService.validatedRoleCodes()).containsExactly("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
        assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.seats()).hasSize(5);
    }

    @Test
    void createRoomRejectsInvalidSavedBoard() {
        ClocktowerRoomService service = ClocktowerRoomTestFactory.context(
                new SavedBoardService(SavedBoardMode.INVALID)).roomService();
        ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, 42L, null,
                List.of(), "HUMAN", false, true, 0);

        assertThatThrownBy(() -> service.create(request, principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_BOARD_INVALID");
    }

    @Test
    void createRoomRejectsSavedBoardWithMismatchedScriptOrPlayerCount() {
        ClocktowerRoomService service = ClocktowerRoomTestFactory.context(
                new SavedBoardService(SavedBoardMode.PLAYER_COUNT_MISMATCH)).roomService();
        ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, 42L, null,
                List.of(), "HUMAN", false, true, 0);

        assertThatThrownBy(() -> service.create(request, principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_BOARD_INVALID");
    }

    @Test
    void joinRoomBindsCurrentUserToRequestedSeat() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "mario"));

        ClocktowerSeatResponse seat = roomService.join(room.roomId(),
                new ClocktowerRoomJoinRequest(2, "Luigi", null), principal(2L, "luigi"));

        assertThat(seat.seatNo()).isEqualTo(2);
        assertThat(seat.userId()).isEqualTo(2L);
        assertThat(seat.displayName()).isEqualTo("Luigi");
    }

    @Test
    void joinRoomRejectsStorytellerAsPlayer() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.join(room.roomId(),
                new ClocktowerRoomJoinRequest(2, "Mario", null), principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_CANNOT_PLAY");
    }

    @Test
    void publicRoomResponseDoesNotExposeAssignedRoles() {
        ClocktowerRoomResponse room = joinedFivePlayerRoom();
        roomService.start(room.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(room.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(room.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(room.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(room.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(room.seats().get(4).seatId(), "IMP")
        ), false), principal(1L, "mario"));

        ClocktowerRoomResponse publicRoom = roomService.get(room.roomId());

        assertThat(publicRoom.seats()).allSatisfy(seat -> {
            assertThat(seat.roleCode()).isNull();
            assertThat(seat.roleType()).isNull();
        });
    }

    @Test
    void onlyStorytellerCanStartRoom() {
        ClocktowerRoomResponse room = joinedFivePlayerRoom();

        assertThatThrownBy(() -> roomService.start(room.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(room.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(room.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(room.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(room.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(room.seats().get(4).seatId(), "IMP")
        ), false), principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_FORBIDDEN");
    }

    @Test
    void onlyStorytellerCanUpdateSeat() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "mario"));

        assertThatThrownBy(() -> roomService.updateSeat(room.roomId(), room.seats().getFirst().seatId(),
                new ClocktowerUpdateSeatRequest("Moved", 5, null), principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_FORBIDDEN");
    }

    @Test
    void storytellerCanAssignLobbySeatRoleAndSeeIt() {
        RbacPrincipal storyteller = principal(1L, "mario");
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), storyteller);

        ClocktowerRoomResponse updated = roomService.updateSeat(room.roomId(), room.seats().getFirst().seatId(),
                new ClocktowerUpdateSeatRequest("Seat 1", 1, "EMPATH"), storyteller);

        assertThat(updated.seats().getFirst().roleCode()).isEqualTo("EMPATH");
        assertThat(updated.seats().getFirst().roleType()).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
        ClocktowerRoomResponse storytellerRoom = roomService.get(room.roomId(), storyteller);
        assertThat(storytellerRoom.seats().getFirst().roleCode()).isEqualTo("EMPATH");

        ClocktowerRoomResponse publicRoom = roomService.get(room.roomId());
        assertThat(publicRoom.seats().getFirst().roleCode()).isNull();
        assertThat(publicRoom.seats().getFirst().roleType()).isNull();
    }

    private ClocktowerRoomResponse joinedFivePlayerRoom() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "mario"));
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        return roomService.get(room.roomId());
    }

    private static ClocktowerRoomCreateRequest createFivePlayerRequest() {
        return new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0);
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }

    private enum SavedBoardMode {
        VALID,
        INVALID,
        PLAYER_COUNT_MISMATCH
    }

    private static final class SavedBoardService implements ClocktowerBoardService {

        private final SavedBoardMode mode;
        private boolean usableBoardCalled;
        private List<String> validatedRoleCodes = List.of();

        private SavedBoardService(SavedBoardMode mode) {
            this.mode = mode;
        }

        private boolean usableBoardCalled() {
            return usableBoardCalled;
        }

        private List<String> validatedRoleCodes() {
            return validatedRoleCodes;
        }

        @Override
        public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
            validatedRoleCodes = request.roleCodes();
            boolean valid = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP").equals(request.roleCodes());
            return new BoardValidationResponse(valid, new ClocktowerRoleTypeCountResponse(3, 0, 1, 1, 0, 0),
                    valid ? List.of() : List.of(new ClocktowerRuleViolationResponse(
                            "BOARD_ROLE_COUNT_MISMATCH", "角色数量必须和玩家人数一致。", "ERROR")), List.of());
        }

        @Override
        public ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.springframework.data.domain.Page<ClocktowerBoardConfigResponse> list(ClocktowerBoardQuery query,
                                                                                        org.springframework.data.domain.Pageable pageable,
                                                                                        RbacPrincipal principal) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        @Override
        public ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
            usableBoardCalled = true;
            if (mode == SavedBoardMode.INVALID) {
                throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
            }
            int playerCount = mode == SavedBoardMode.PLAYER_COUNT_MISMATCH ? 6 : 5;
            return new ClocktowerBoardConfigResponse(boardConfigId, "CTB-TEST", ClocktowerScriptCode.TROUBLE_BREWING,
                    playerCount, true, java.time.Instant.parse("2026-06-19T03:00:00Z"),
                    List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), List.of(),
                    new ClocktowerBoardValidationResponse(true,
                            Map.of("TOWNSFOLK", 3, "MINION", 1, "DEMON", 1), List.of(), List.of()));
        }

        @Override
        public void delete(Long boardId, RbacPrincipal principal) {
        }
    }

    private static final class RejectEmptyBoardService implements ClocktowerBoardService {

        @Override
        public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
            if (request.roleCodes().isEmpty()) {
                return new BoardValidationResponse(false, new ClocktowerRoleTypeCountResponse(0, 0, 0, 0, 0, 0),
                        List.of(), List.of());
            }
            return new BoardValidationResponse(true, new ClocktowerRoleTypeCountResponse(3, 0, 1, 1, 0, 0),
                    List.of(), List.of());
        }

        @Override
        public ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.springframework.data.domain.Page<ClocktowerBoardConfigResponse> list(ClocktowerBoardQuery query,
                                                                                        org.springframework.data.domain.Pageable pageable,
                                                                                        RbacPrincipal principal) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        @Override
        public ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Long boardId, RbacPrincipal principal) {
        }
    }
}
