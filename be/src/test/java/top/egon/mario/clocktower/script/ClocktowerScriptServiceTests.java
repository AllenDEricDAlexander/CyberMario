package top.egon.mario.clocktower.script;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
import top.egon.mario.clocktower.script.repository.ClocktowerJinxRuleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerScriptRepository;
import top.egon.mario.clocktower.script.service.impl.ClocktowerScriptServiceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClocktowerScriptServiceTests {

    @Test
    void listScriptsReturnsEnabledScriptsInSortOrder() {
        ClocktowerScriptRepository repository = mock(ClocktowerScriptRepository.class);
        ClocktowerScriptPo trouble = script(ClocktowerScriptCode.TROUBLE_BREWING, "暗流涌动", 5, 15, 10);
        ClocktowerScriptPo bmr = script(ClocktowerScriptCode.BAD_MOON_RISING, "黯月初升", 7, 15, 20);
        given(repository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc()).willReturn(List.of(trouble, bmr));

        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(repository, null, null, null);

        List<ClocktowerScriptResponse> scripts = service.listScripts();

        assertThat(scripts).extracting(ClocktowerScriptResponse::scriptCode)
                .containsExactly(ClocktowerScriptCode.TROUBLE_BREWING, ClocktowerScriptCode.BAD_MOON_RISING);
        assertThat(scripts.getFirst().minPlayers()).isEqualTo(5);
    }

    @Test
    void listRolesExposesLocalizedRoleNameAndAlignment() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(scriptRepository, roleRepository, null,
                mock(ClocktowerJinxRuleRepository.class));
        ClocktowerRolePo chef = role("CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD);
        given(roleRepository.findByScriptCodeAndRoleTypeAndDeletedFalseOrderBySortOrderAsc(
                ClocktowerScriptCode.TROUBLE_BREWING, ClocktowerRoleType.TOWNSFOLK)).willReturn(List.of(chef));

        List<ClocktowerRoleResponse> roles = service.listRoles(ClocktowerScriptCode.TROUBLE_BREWING, "镇民", null);

        assertThat(roles).hasSize(1);
        assertThat(roles.getFirst().roleCode()).isEqualTo("CHEF");
        assertThat(roles.getFirst().roleName()).isEqualTo("厨师");
        assertThat(roles.getFirst().name()).isEqualTo("厨师");
        assertThat(roles.getFirst().roleType()).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
        assertThat(roles.getFirst().alignment()).isEqualTo(ClocktowerAlignment.GOOD);
        assertThat(roles.getFirst().abilityText()).isEqualTo("厨师能力");
    }

    @Test
    void nightOrderExposesLocalizedNightTypeAndOrderNo() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerNightOrderRepository nightOrderRepository = mock(ClocktowerNightOrderRepository.class);
        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(scriptRepository, roleRepository,
                nightOrderRepository, mock(ClocktowerJinxRuleRepository.class));
        ClocktowerRolePo poisoner = role("POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL);
        ClocktowerNightOrderPo poisonerOrder = nightOrder("POISONER", ClocktowerNightType.FIRST_NIGHT, 5);
        given(nightOrderRepository.findByScriptCodeAndNightTypeAndDeletedFalseOrderBySortOrderAsc(
                ClocktowerScriptCode.TROUBLE_BREWING, ClocktowerNightType.FIRST_NIGHT)).willReturn(List.of(poisonerOrder));
        given(roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode.TROUBLE_BREWING))
                .willReturn(List.of(poisoner));

        List<ClocktowerNightOrderResponse> orders = service.nightOrder(ClocktowerScriptCode.TROUBLE_BREWING, "1");

        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().roleCode()).isEqualTo("POISONER");
        assertThat(orders.getFirst().roleName()).isEqualTo("投毒者");
        assertThat(orders.getFirst().roleType()).isEqualTo(ClocktowerRoleType.MINION);
        assertThat(orders.getFirst().nightType()).isEqualTo(ClocktowerNightType.FIRST_NIGHT);
        assertThat(orders.getFirst().orderNo()).isEqualTo(5);
        assertThat(orders.getFirst().sortOrder()).isEqualTo(105);
        assertThat(orders.getFirst().reminderText()).isEqualTo("POISONER提醒");
    }

    @Test
    void groupedNightOrderSplitsFirstAndOtherNightResponses() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerNightOrderRepository nightOrderRepository = mock(ClocktowerNightOrderRepository.class);
        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(scriptRepository, roleRepository,
                nightOrderRepository, mock(ClocktowerJinxRuleRepository.class));
        ClocktowerRolePo poisoner = role("POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL);
        ClocktowerRolePo empath = role("EMPATH", "共情者", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD);
        given(nightOrderRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(
                ClocktowerScriptCode.TROUBLE_BREWING)).willReturn(List.of(
                nightOrder("POISONER", ClocktowerNightType.FIRST_NIGHT, 5),
                nightOrder("EMPATH", ClocktowerNightType.OTHER_NIGHT, 20)));
        given(roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode.TROUBLE_BREWING))
                .willReturn(List.of(poisoner, empath));

        ClocktowerNightOrderGroupResponse group = service.groupedNightOrder(ClocktowerScriptCode.TROUBLE_BREWING);

        assertThat(group.firstNight()).extracting(ClocktowerNightOrderResponse::roleCode)
                .containsExactly("POISONER");
        assertThat(group.otherNight()).extracting(ClocktowerNightOrderResponse::roleCode)
                .containsExactly("EMPATH");
    }

    private static ClocktowerScriptPo script(ClocktowerScriptCode code, String name, int min, int max, int order) {
        ClocktowerScriptPo po = new ClocktowerScriptPo();
        po.setScriptCode(code);
        po.setName(name);
        po.setEdition("BASE_3");
        po.setMinPlayers(min);
        po.setMaxPlayers(max);
        po.setRoleCount(22);
        po.setEnabled(true);
        po.setSortOrder(order);
        return po;
    }

    private static ClocktowerRolePo role(String roleCode, String name, ClocktowerRoleType roleType,
                                         ClocktowerAlignment alignment) {
        ClocktowerRolePo po = new ClocktowerRolePo();
        po.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        po.setRoleCode(roleCode);
        po.setName(name);
        po.setRoleType(roleType);
        po.setAlignment(alignment);
        po.setAbilityText(name + "能力");
        po.setFirstNightReminder(name + "首夜提醒");
        po.setOtherNightReminder(name + "其他夜提醒");
        po.setEnabled(true);
        return po;
    }

    private static ClocktowerNightOrderPo nightOrder(String roleCode, ClocktowerNightType nightType, int orderNo) {
        ClocktowerNightOrderPo po = new ClocktowerNightOrderPo();
        po.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        po.setRoleCode(roleCode);
        po.setNightType(nightType);
        po.setOrderNo(orderNo);
        po.setSortOrder(orderNo + 100);
        po.setReminderText(roleCode + "提醒");
        return po;
    }
}
