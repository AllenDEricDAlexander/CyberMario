package top.egon.mario.clocktower.flow;

import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.flow.service.impl.ClocktowerFlowServiceImpl;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.ruling.service.impl.ClocktowerRulingServiceImpl;

final class TestClocktowerFlowServices {

    private TestClocktowerFlowServices() {
    }

    static ClocktowerFlowService flowService(ClocktowerRoomTestFactory.Context context) {
        ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
        ClocktowerRuleEngine ruleEngine = new ClocktowerRuleEngine(
                configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
                configuration.clocktowerFlowKieBase(new DefaultResourceLoader()));
        ClocktowerGrimoireServiceImpl grimoireService = grimoireService(context);
        ClocktowerRulingServiceImpl rulingService = new ClocktowerRulingServiceImpl(context.roomRepository(),
                context.seatRepository(), context.nominationRepository(), context.rulingRepository(),
                context.eventRepository(), context.eventService(), context.objectMapper(), grimoireService);
        return new ClocktowerFlowServiceImpl(context.roomRepository(), context.seatRepository(),
                context.storytellerTaskRepository(), context.nominationRepository(), context.voteRepository(),
                context.roleRepository(), context.eventService(), context.eventRepository(), rulingService,
                grimoireService, ruleEngine);
    }

    static ClocktowerGrimoireServiceImpl grimoireService(ClocktowerRoomTestFactory.Context context) {
        return new ClocktowerGrimoireServiceImpl(context.roomRepository(), context.seatRepository(),
                context.grimoireEntryRepository(), context.markerRepository(), context.storytellerTaskRepository(),
                context.nightOrderRepository(), context.roleRepository(), context.eventService());
    }
}
