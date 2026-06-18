package top.egon.mario.clocktower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleCode;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.converter.jpa.ClocktowerAlignmentConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerNightTypeConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerRoleTypeConverter;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerJpaMappingTests {

    @Test
    void clocktowerEnumsExposePhaseOneContracts() {
        assertThat(ClocktowerScriptCode.valueOf("TROUBLE_BREWING")).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(ClocktowerRoomStatus.valueOf("LOBBY")).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(ClocktowerPhase.valueOf("FIRST_NIGHT")).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
    }

    @Test
    void rulingEnumsAndSeatPublicLifeStatusExposePhaseOneContracts() throws Exception {
        assertThat(ClocktowerRulingType.valueOf("MARK_DEAD")).isEqualTo(ClocktowerRulingType.MARK_DEAD);
        assertThat(ClocktowerRulingReason.valueOf("ROLE_ABILITY")).isEqualTo(ClocktowerRulingReason.ROLE_ABILITY);
        assertThat(ClocktowerRulingStatus.valueOf("APPLIED")).isEqualTo(ClocktowerRulingStatus.APPLIED);

        Field publicLifeStatus = ClocktowerSeatPo.class.getDeclaredField("publicLifeStatus");
        assertThat(publicLifeStatus.getAnnotation(Column.class).name()).isEqualTo("public_life_status");

        ClocktowerSeatPo seat = new ClocktowerSeatPo();
        assertThat(seat.getLifeStatus()).isEqualTo("ALIVE");
        assertThat(seat.getPublicLifeStatus()).isEqualTo("ALIVE");
    }

    @Test
    void clocktowerCodedEnumsExposeChineseDescriptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode roleTypeJson = mapper.valueToTree(ClocktowerRoleType.TOWNSFOLK);
        JsonNode alignmentJson = mapper.valueToTree(ClocktowerAlignment.GOOD);
        JsonNode nightTypeJson = mapper.valueToTree(ClocktowerNightType.FIRST_NIGHT);

        assertThat(roleTypeJson.get("code").asInt()).isEqualTo(1);
        assertThat(roleTypeJson.get("desc").asText()).isEqualTo("镇民");
        assertThat(alignmentJson.get("code").asInt()).isEqualTo(1);
        assertThat(alignmentJson.get("desc").asText()).isEqualTo("善良");
        assertThat(nightTypeJson.get("code").asInt()).isEqualTo(1);
        assertThat(nightTypeJson.get("desc").asText()).isEqualTo("首夜");

        assertThat(mapper.convertValue(1, ClocktowerRoleType.class)).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
        assertThat(mapper.convertValue("邪恶", ClocktowerAlignment.class)).isEqualTo(ClocktowerAlignment.EVIL);
        assertThat(mapper.convertValue("NEUTRAL", ClocktowerAlignment.class)).isEqualTo(ClocktowerAlignment.NEUTRAL);
        assertThat(mapper.convertValue("OTHER_NIGHT", ClocktowerNightType.class)).isEqualTo(
                ClocktowerNightType.OTHER_NIGHT);
    }

    @Test
    void clocktowerRoleCodesExposeChineseDescriptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode roleCodeJson = mapper.valueToTree(ClocktowerRoleCode.CHEF);

        assertThat(roleCodeJson.get("code").asInt()).isEqualTo(1);
        assertThat(roleCodeJson.get("desc").asText()).isEqualTo("厨师");
        assertThat(ClocktowerRoleCode.fromJson("IMP")).isEqualTo(ClocktowerRoleCode.IMP);
        assertThat(ClocktowerRoleCode.fromJson("小恶魔")).isEqualTo(ClocktowerRoleCode.IMP);
        assertThat(ClocktowerRoleCode.values()).hasSize(72);
    }

    @Test
    void clocktowerRulePersistenceFieldsUseCodedEnumConverters() throws Exception {
        Field roleType = ClocktowerRolePo.class.getDeclaredField("roleType");
        Field alignment = ClocktowerRolePo.class.getDeclaredField("alignment");
        Field nightType = ClocktowerNightOrderPo.class.getDeclaredField("nightType");

        assertThat(roleType.getType()).isEqualTo(ClocktowerRoleType.class);
        assertThat(alignment.getType()).isEqualTo(ClocktowerAlignment.class);
        assertThat(nightType.getType()).isEqualTo(ClocktowerNightType.class);
        assertThat(roleType.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerRoleTypeConverter.class);
        assertThat(alignment.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerAlignmentConverter.class);
        assertThat(nightType.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerNightTypeConverter.class);
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
