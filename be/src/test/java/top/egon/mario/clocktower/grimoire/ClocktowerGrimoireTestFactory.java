package top.egon.mario.clocktower.grimoire;

import top.egon.mario.clocktower.grimoire.repository.ClocktowerStatusMarkerRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ClocktowerGrimoireTestFactory {

    private ClocktowerGrimoireTestFactory() {
    }

    static Services services() {
        ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
        ClocktowerStatusMarkerRepository markerRepository = mock(ClocktowerStatusMarkerRepository.class);
        ClocktowerStorytellerTaskRepository taskRepository = mock(ClocktowerStorytellerTaskRepository.class);
        when(markerRepository.findByRoomIdAndActiveTrueAndDeletedFalseOrderByIdAsc(any())).thenReturn(List.of());
        when(taskRepository.findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(any(), eq("PENDING")))
                .thenReturn(List.of());
        ClocktowerGrimoireService grimoireService = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
                context.seatRepository(), context.grimoireEntryRepository(), markerRepository, taskRepository,
                context.nightOrderRepository(), context.roleRepository(), context.eventService());
        return new Services(context.roomService(), grimoireService);
    }

    record Services(ClocktowerRoomService roomService, ClocktowerGrimoireService grimoireService) {
    }
}
