package top.egon.mario.clocktower;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerJpaMappingTests {

    @Test
    void clocktowerEnumsExposePhaseOneContracts() {
        assertThat(ClocktowerScriptCode.valueOf("TROUBLE_BREWING")).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(ClocktowerRoomStatus.valueOf("LOBBY")).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(ClocktowerPhase.valueOf("FIRST_NIGHT")).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
    }

    @Test
    void roomAndScriptEntitiesUseProjectAuditBaseClass() {
        ClocktowerScriptPo script = new ClocktowerScriptPo();
        script.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        script.setName("暗流涌动");

        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setRoomCode("ABC123");
        room.setStatus(ClocktowerRoomStatus.LOBBY);
        room.setPhase(ClocktowerPhase.LOBBY);

        assertThat(script.getScriptCode()).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(room.getStatus()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.isDeleted()).isFalse();
    }
}
