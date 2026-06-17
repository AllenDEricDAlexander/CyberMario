package top.egon.mario.clocktower.script;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
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
}
