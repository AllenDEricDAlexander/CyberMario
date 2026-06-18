package top.egon.mario.clocktower.script.service;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerJinxRuleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerTermResponse;

import java.util.List;

public interface ClocktowerScriptService {

    List<ClocktowerScriptResponse> listScripts();

    ClocktowerScriptResponse getScript(ClocktowerScriptCode scriptCode);

    List<ClocktowerRoleResponse> listRoles(ClocktowerScriptCode scriptCode, String roleType, Boolean enabled);

    List<ClocktowerNightOrderResponse> nightOrder(ClocktowerScriptCode scriptCode, String nightType);

    ClocktowerNightOrderGroupResponse groupedNightOrder(ClocktowerScriptCode scriptCode);

    List<ClocktowerTermResponse> terms(String keyword, String category);

    List<ClocktowerJinxRuleResponse> jinxRules(String roleCode, String severity);
}
