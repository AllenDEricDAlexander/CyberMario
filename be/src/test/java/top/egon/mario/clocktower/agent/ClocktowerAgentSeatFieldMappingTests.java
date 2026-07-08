package top.egon.mario.clocktower.agent;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentSeatFieldMappingTests {

    @Autowired
    private EntityManager entityManager;

    @Test
    void roomSeatActorFieldsPersistAndReload() {
        ClocktowerRoomSeatPo seat = new ClocktowerRoomSeatPo();
        seat.setRoomId(81001L);
        seat.setSeatNo(1);
        seat.setUserId(null);
        seat.setDisplayName("Agent 1");
        seat.setRoleCode("CHEF");
        seat.setStatus("OCCUPIED");
        seat.setActorId(82001L);
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(83001L);
        seat.setMetadataJson("{\"ready\":true,\"agentSeat\":true}");
        entityManager.persist(seat);

        entityManager.flush();
        entityManager.clear();

        ClocktowerRoomSeatPo reloaded = entityManager.find(ClocktowerRoomSeatPo.class, seat.getId());
        assertThat(reloaded.getActorId()).isEqualTo(82001L);
        assertThat(reloaded.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(reloaded.getAgentInstanceId()).isEqualTo(83001L);
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void gameSeatActorFieldsPersistAndReload() {
        ClocktowerGameSeatPo seat = new ClocktowerGameSeatPo();
        seat.setGameId(84001L);
        seat.setRoomSeatId(85001L);
        seat.setSeatNo(2);
        seat.setUserId(null);
        seat.setDisplayName("Agent 2");
        seat.setRoleCode("EMPATH");
        seat.setStatus("ACTIVE");
        seat.setActorId(86001L);
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(87001L);
        seat.setMetadataJson("{\"ready\":true,\"agentSeat\":true}");
        entityManager.persist(seat);

        entityManager.flush();
        entityManager.clear();

        ClocktowerGameSeatPo reloaded = entityManager.find(ClocktowerGameSeatPo.class, seat.getId());
        assertThat(reloaded.getActorId()).isEqualTo(86001L);
        assertThat(reloaded.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(reloaded.getAgentInstanceId()).isEqualTo(87001L);
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void newSeatObjectsDefaultToHumanActorType() {
        assertThat(new ClocktowerRoomSeatPo().getActorType()).isEqualTo(ClocktowerActorType.HUMAN);
        assertThat(new ClocktowerGameSeatPo().getActorType()).isEqualTo(ClocktowerActorType.HUMAN);
    }
}
