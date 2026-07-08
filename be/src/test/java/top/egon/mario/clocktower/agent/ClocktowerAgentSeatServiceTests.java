package top.egon.mario.clocktower.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentSeatServiceTests {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ClocktowerAgentSeatService agentSeatService;

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository instanceRepository;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAgentForRoomSeatCreatesActorInstanceAndBindsRoomSeat() throws Exception {
        ClocktowerRoomSeatPo seat = openRoomSeat(77001L, 3, "CHEF");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                77001L, seat.getId(), 3, null, "CHEF", null, null);

        assertThat(descriptor.actorId()).isNotNull();
        assertThat(descriptor.agentInstanceId()).isNotNull();
        assertThat(descriptor.roomId()).isEqualTo(77001L);
        assertThat(descriptor.roomSeatId()).isEqualTo(seat.getId());
        assertThat(descriptor.seatNo()).isEqualTo(3);
        assertThat(descriptor.displayName()).isEqualTo("Agent 3");
        assertThat(descriptor.roleCode()).isEqualTo("CHEF");
        assertThat(descriptor.profileName()).isEqualTo("balanced");
        assertThat(descriptor.autoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);
        assertThat(descriptor.metadata())
                .containsEntry("ready", true)
                .containsEntry("actorType", ClocktowerActorType.AGENT)
                .containsEntry("agent", true)
                .containsEntry("agentSeat", true)
                .containsEntry("systemManaged", true)
                .containsEntry("agentPolicy", "HEURISTIC_V0")
                .containsEntry("autoMode", ClocktowerAgentAutoMode.FULL_AUTO)
                .containsEntry("createdBy", "agentSeatCount");

        ClocktowerActorPo actor = actorRepository.findByIdAndDeletedFalse(descriptor.actorId()).orElseThrow();
        assertThat(actor.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(actor.getUserId()).isNull();
        assertThat(actor.getDisplayName()).isEqualTo("Agent 3");

        ClocktowerAgentInstancePo instance = instanceRepository
                .findByActorIdAndDeletedFalse(descriptor.actorId())
                .orElseThrow();
        assertThat(instance.getId()).isEqualTo(descriptor.agentInstanceId());
        assertThat(instance.getRoomId()).isEqualTo(77001L);
        assertThat(instance.getRoomSeatId()).isEqualTo(seat.getId());
        assertThat(instance.getAutoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);

        ClocktowerRoomSeatPo boundSeat = roomSeatRepository.findById(seat.getId()).orElseThrow();
        assertThat(boundSeat.getUserId()).isNull();
        assertThat(boundSeat.getActorId()).isEqualTo(descriptor.actorId());
        assertThat(boundSeat.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(boundSeat.getAgentInstanceId()).isEqualTo(descriptor.agentInstanceId());
        assertThat(boundSeat.getDisplayName()).isEqualTo("Agent 3");
        assertThat(boundSeat.getRoleCode()).isEqualTo("CHEF");
        assertThat(boundSeat.getStatus()).isEqualTo("OCCUPIED");

        Map<String, Object> metadata = objectMapper.readValue(boundSeat.getMetadataJson(), MAP_TYPE);
        assertThat(agentSeatService.isSystemAgentSeat(
                boundSeat.getActorType(), boundSeat.getAgentInstanceId(), metadata)).isTrue();
    }

    @Test
    void createAgentForRoomSeatSupportsExplicitProfileDisplayNameAndAutoMode() {
        ClocktowerRoomSeatPo seat = openRoomSeat(78001L, 2, "EMPATH");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                78001L, seat.getId(), 2, "Quiet Bot", "EMPATH", "quiet", ClocktowerAgentAutoMode.ST_APPROVAL);

        assertThat(descriptor.displayName()).isEqualTo("Quiet Bot");
        assertThat(descriptor.profileName()).isEqualTo("quiet");
        assertThat(descriptor.autoMode()).isEqualTo(ClocktowerAgentAutoMode.ST_APPROVAL);
        assertThat(agentSeatService.agentsOfRoom(78001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .containsExactly(descriptor.agentInstanceId());
    }

    @Test
    void createAgentForRoomSeatRejectsUnknownProfileMismatchedSeatAndDuplicateBinding() {
        ClocktowerRoomSeatPo seat = openRoomSeat(79001L, 1, "MONK");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", "missing-profile", null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_PROFILE_NOT_FOUND");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79002L, seat.getId(), 1, null, "MONK", null, null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_ROOM_SEAT_MISMATCH");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", null, null);
        assertThat(descriptor.agentInstanceId()).isNotNull();

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", null, null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_SEAT_ALREADY_BOUND");
    }

    @Test
    void createAgentForRoomSeatRejectsInvalidAutoMode() {
        ClocktowerRoomSeatPo seat = openRoomSeat(80001L, 1, "IMP");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                80001L, seat.getId(), 1, null, "IMP", null, "ROBOT"))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_AUTO_MODE_INVALID");
    }

    @Test
    void isSystemAgentSeatRequiresAllStrongSignals() {
        Map<String, Object> validMetadata = Map.of(
                "systemManaged", true,
                "createdBy", "agentSeatCount",
                "agent", true
        );

        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, validMetadata)).isTrue();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.HUMAN, 90001L, validMetadata)).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, null, validMetadata)).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, Map.of("agent", true))).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, Map.of("systemManaged", true, "createdBy", "manual"))).isFalse();
    }

    @Test
    void agentsOfGameReturnsLinkedInstances() {
        ClocktowerRoomSeatPo seat = openRoomSeat(81001L, 4, "POISONER");
        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                81001L, seat.getId(), 4, null, "POISONER", null, null);
        ClocktowerAgentInstancePo instance = instanceRepository.findByIdAndDeletedFalse(
                descriptor.agentInstanceId()).orElseThrow();
        instance.setGameId(82001L);
        instanceRepository.saveAndFlush(instance);

        assertThat(agentSeatService.agentsOfGame(82001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .containsExactly(descriptor.agentInstanceId());
        assertThat(agentSeatService.agentsOfGame(null)).isEmpty();
    }

    private ClocktowerRoomSeatPo openRoomSeat(Long roomId, int seatNo, String roleCode) {
        ClocktowerRoomSeatPo seat = new ClocktowerRoomSeatPo();
        seat.setRoomId(roomId);
        seat.setSeatNo(seatNo);
        seat.setDisplayName("Seat " + seatNo);
        seat.setRoleCode(roleCode);
        seat.setStatus("OPEN");
        seat.setMetadataJson("{\"ready\":false}");
        return roomSeatRepository.saveAndFlush(seat);
    }
}
