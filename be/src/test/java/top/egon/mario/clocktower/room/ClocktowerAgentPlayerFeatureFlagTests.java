package top.egon.mario.clocktower.room;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent-player.enabled=false"
})
@Transactional
class ClocktowerAgentPlayerFeatureFlagTests {

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Test
    void disabledAgentPlayerRejectsNonZeroAgentSeatCount() {
        assertThatThrownBy(() -> roomService.createRoom(createRequest(4), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_PLAYER_DISABLED");
    }

    @Test
    void disabledAgentPlayerKeepsZeroAgentRoomCreationAvailable() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(0), owner());

        assertThat(room.seats()).hasSize(5);
        assertThat(room.seats()).allSatisfy(seat -> assertThat(seat.isAgent()).isFalse());
    }

    private ClocktowerRoomCreateRequest createRequest(int agentSeatCount) {
        return new ClocktowerRoomCreateRequest("No Agents", ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, null, List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN_ST", false, false, agentSeatCount, "PUBLIC", "OPEN_SEATING");
    }

    private RbacPrincipal owner() {
        return new RbacPrincipal(1L, "owner", Set.of(), Set.of(), "test");
    }
}
