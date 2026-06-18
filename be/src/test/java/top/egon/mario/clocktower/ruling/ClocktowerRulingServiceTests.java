package top.egon.mario.clocktower.ruling;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.clocktower.ruling.service.impl.ClocktowerRulingServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerRulingServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerRulingService rulingService = new ClocktowerRulingServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.rulingRepository(),
            context.eventService(), context.objectMapper(), new ClocktowerGrimoireServiceImpl(context.roomRepository(),
            context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
            context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
            context.eventService()));

    @Test
    void markDeadUpdatesRealAndPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.MARK_DEAD, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(response.ruling().rulingType()).isEqualTo(ClocktowerRulingType.MARK_DEAD);
        assertThat(response.events()).extracting(event -> event.eventType()).contains(ClocktowerEventType.PLAYER_DIED);
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("DEAD");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("DEAD");
    }

    @Test
    void setPublicLifeOnlyChangesPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.SET_PUBLIC_LIFE, targetSeatId, null, null, "DEAD", null,
                ClocktowerRulingReason.STORYTELLER_RULING, "假死", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("ALIVE");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("DEAD");
    }

    @Test
    void publicLifePublicEventHidesInternalRulingMetadata() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.SET_PUBLIC_LIFE, targetSeatId, null, null, "DEAD", null,
                ClocktowerRulingReason.STORYTELLER_RULING, "假死", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        Map<String, Object> payload = response.events().getFirst().payload();
        assertThat(payload).containsEntry("publicNote", "一名玩家死亡");
        assertThat(payload).doesNotContainKeys("rulingType", "reason");
    }

    @Test
    void setPublicLifeRejectsInvalidPublicLifeStatus() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        assertThatThrownBy(() -> rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.SET_PUBLIC_LIFE, targetSeatId, null, null, "UNKNOWN", null,
                ClocktowerRulingReason.STORYTELLER_RULING, "假死", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal())).isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_PUBLIC_LIFE_STATUS_INVALID");
    }

    @Test
    void rejectsInvalidNonblankWinner() {
        ClocktowerRoomResponse room = startedRoom();

        assertThatThrownBy(() -> rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.END_GAME, null, null, null, null, "UNKNOWN",
                ClocktowerRulingReason.STORYTELLER_RULING, "测试局结束", "游戏结束", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal())).isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_RULING_WINNER_INVALID");
    }

    @Test
    void restoreAliveUpdatesRealAndPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();
        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.MARK_DEAD, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.RESTORE_ALIVE, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.ROLE_ABILITY, "角色能力复活", "一名玩家复活", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("ALIVE");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("ALIVE");
    }

    @Test
    void endGameRequiresNoteAndCanOmitWinner() {
        ClocktowerRoomResponse room = startedRoom();

        assertThatThrownBy(() -> rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.END_GAME, null, null, null, null, null,
                ClocktowerRulingReason.STORYTELLER_RULING, "", "游戏结束", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal())).isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_RULING_NOTE_REQUIRED");

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.END_GAME, null, null, null, null, null,
                ClocktowerRulingReason.STORYTELLER_RULING, "测试局结束", "游戏结束", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.roomRepository().findByIdAndDeletedFalse(room.roomId()).orElseThrow().getStatus())
                .isEqualTo(ClocktowerRoomStatus.ENDED);
    }

    private ClocktowerRoomResponse startedRoom() {
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), "HUMAN", false, true, 0),
                storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        ClocktowerRoomResponse joined = roomService.get(room.roomId());
        roomService.start(joined.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storytellerPrincipal());
        return roomService.get(joined.roomId());
    }

    private static RbacPrincipal storytellerPrincipal() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
