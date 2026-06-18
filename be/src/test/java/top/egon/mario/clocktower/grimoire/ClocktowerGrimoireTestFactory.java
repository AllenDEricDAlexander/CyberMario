package top.egon.mario.clocktower.grimoire;

import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;

final class ClocktowerGrimoireTestFactory {

    private ClocktowerGrimoireTestFactory() {
    }

    static Services services() {
        ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
        ClocktowerGrimoireService grimoireService = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
                context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
                context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
                context.eventService());
        return new Services(context.roomService(), grimoireService, context.nightOrderRepository());
    }

    record Services(ClocktowerRoomService roomService, ClocktowerGrimoireService grimoireService,
                    ClocktowerNightOrderRepository nightOrderRepository) {
    }
}
