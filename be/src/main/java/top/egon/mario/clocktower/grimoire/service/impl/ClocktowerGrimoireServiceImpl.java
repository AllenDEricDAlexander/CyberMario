package top.egon.mario.clocktower.grimoire.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GrimoireSeatResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StatusMarkerResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerTaskResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerGrimoireEntryRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStatusMarkerRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerGrimoireServiceImpl implements ClocktowerGrimoireService {

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerGrimoireEntryRepository grimoireEntryRepository;
    private final ClocktowerStatusMarkerRepository markerRepository;
    private final ClocktowerStorytellerTaskRepository taskRepository;

    @Override
    public ClocktowerGrimoireResponse getGrimoire(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        Map<Long, ClocktowerGrimoireEntryPo> entries = grimoireEntryRepository
                .findByRoomIdAndDeletedFalseOrderBySeatIdAsc(roomId)
                .stream()
                .collect(Collectors.toMap(ClocktowerGrimoireEntryPo::getSeatId, Function.identity(), (left, right) -> left));
        return new ClocktowerGrimoireResponse(roomId, GamePhaseResponse.from(room),
                seats.stream().map(seat -> GrimoireSeatResponse.from(seat, entries.get(seat.getId()))).toList(),
                markerRepository.findByRoomIdAndActiveTrueAndDeletedFalseOrderByIdAsc(roomId).stream()
                        .map(StatusMarkerResponse::from)
                        .toList(),
                List.of(),
                taskRepository.findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING").stream()
                        .map(StorytellerTaskResponse::from)
                        .toList(),
                false);
    }
}
