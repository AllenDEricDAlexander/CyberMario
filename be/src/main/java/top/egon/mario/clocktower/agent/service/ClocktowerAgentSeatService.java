package top.egon.mario.clocktower.agent.service;

import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;

import java.util.List;
import java.util.Map;

public interface ClocktowerAgentSeatService {

    ClocktowerAgentSeatDescriptor createAgentForRoomSeat(Long roomId,
                                                         Long roomSeatId,
                                                         int seatNo,
                                                         String displayName,
                                                         String roleCode,
                                                         String profileName,
                                                         String autoMode);

    boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata);

    List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId);

    List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId);
}
