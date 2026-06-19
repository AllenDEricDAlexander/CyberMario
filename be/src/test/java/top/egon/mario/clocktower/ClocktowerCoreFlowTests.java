package top.egon.mario.clocktower;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.action.dto.ClocktowerActionRequest;
import top.egon.mario.clocktower.action.dto.ClocktowerActionResponse;
import top.egon.mario.clocktower.action.service.ClocktowerActionService;
import top.egon.mario.clocktower.action.service.impl.ClocktowerActionServiceImpl;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.flow.ClocktowerFlowService;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.clocktower.flow.service.impl.ClocktowerFlowServiceImpl;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.replay.service.ClocktowerReplayService;
import top.egon.mario.clocktower.replay.service.impl.ClocktowerReplayServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.ruling.service.impl.ClocktowerRulingServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerCoreFlowTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerGrimoireService grimoireService = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
            context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
            context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
            context.eventService());
    private final ClocktowerFlowService flowService = flowService();
    private final ClocktowerActionService actionService = new ClocktowerActionServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.voteRepository(), context.eventService());
    private final ClocktowerReplayService replayService = new ClocktowerReplayServiceImpl(context.roomRepository(),
            context.eventRepository(), context.voteRepository(), context.objectMapper());

    @Test
    void storytellerCanRunCoreFivePlayerTroubleBrewingFlow() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "storyteller"));
        ClocktowerRoomResponse joined = joinAllSeats(room);
        ClocktowerStartGameResponse started = roomService.start(joined.roomId(), fixedAssignments(joined),
                principal(1L, "storyteller"));

        assertThat(started.phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);

        grimoireService.getGrimoire(joined.roomId(), principal(1L, "storyteller"));
        context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(joined.roomId(), "PENDING")
                .forEach(task -> flowService.skipNightTask(joined.roomId(), task.getId(),
                        new SkipNightTaskRequest("核心流程测试跳过"), principal(1L, "storyteller")));
        assertThat(flowService.advance(joined.roomId(), principal(1L, "storyteller")).phase().phase())
                .isEqualTo(ClocktowerPhase.DAY);

        Long seatOne = joined.seats().getFirst().seatId();
        Long seatTwo = joined.seats().get(1).seatId();
        ClocktowerActionResponse nominate = actionService.submit(joined.roomId(),
                new ClocktowerActionRequest(seatOne, "NOMINATE", List.of(seatTwo), null, "我提名 2 号。", Map.of(), "nom-1"),
                principal(2L, "player1"));

        assertThat(nominate.accepted()).isTrue();
        assertThat(replayService.replay(joined.roomId(), "PUBLIC", null, null, principal(2L, "player1")).events())
                .extracting(ClocktowerEventResponse::eventType)
                .contains(ClocktowerEventType.PLAYER_NOMINATED);
    }

    private ClocktowerRoomCreateRequest createFivePlayerRequest() {
        return new ClocktowerRoomCreateRequest("核心流程", ClocktowerScriptCode.TROUBLE_BREWING, 5,
                null, null, List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0);
    }

    private ClocktowerFlowService flowService() {
        ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
        ClocktowerRuleEngine ruleEngine = new ClocktowerRuleEngine(
                configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
                configuration.clocktowerFlowKieBase(new DefaultResourceLoader()));
        ClocktowerRulingServiceImpl rulingService = new ClocktowerRulingServiceImpl(context.roomRepository(),
                context.seatRepository(), context.nominationRepository(), context.rulingRepository(),
                context.eventRepository(), context.eventService(), context.objectMapper(), grimoireService);
        return new ClocktowerFlowServiceImpl(context.roomRepository(), context.seatRepository(),
                context.storytellerTaskRepository(), context.nominationRepository(), context.voteRepository(),
                context.roleRepository(), context.eventService(), context.eventRepository(), rulingService, ruleEngine);
    }

    private ClocktowerRoomResponse joinAllSeats(ClocktowerRoomResponse room) {
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player" + (i + 1)));
        }
        return roomService.get(room.roomId());
    }

    private ClocktowerRoomStartRequest fixedAssignments(ClocktowerRoomResponse room) {
        return new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(room.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(room.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(room.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(room.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(room.seats().get(4).seatId(), "IMP")
        ), false);
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER", "CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
