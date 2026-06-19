package top.egon.mario.clocktower.flow;

import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.flow.service.impl.ClocktowerFlowServiceImpl;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;

final class TestClocktowerFlowServices {

    private TestClocktowerFlowServices() {
    }

    static ClocktowerFlowService flowService(ClocktowerRoomTestFactory.Context context) {
        ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
        ClocktowerRuleEngine ruleEngine = new ClocktowerRuleEngine(
                configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
                configuration.clocktowerFlowKieBase(new DefaultResourceLoader()));
        return new ClocktowerFlowServiceImpl(context.roomRepository(), context.seatRepository(),
                context.storytellerTaskRepository(), context.nominationRepository(), context.voteRepository(),
                context.roleRepository(), context.eventService(), context.eventRepository(), ruleEngine);
    }

    static ClocktowerGrimoireServiceImpl grimoireService(ClocktowerRoomTestFactory.Context context) {
        return new ClocktowerGrimoireServiceImpl(context.roomRepository(), context.seatRepository(),
                context.grimoireEntryRepository(), context.markerRepository(), context.storytellerTaskRepository(),
                context.nightOrderRepository(), context.roleRepository(), context.eventService());
    }
}
