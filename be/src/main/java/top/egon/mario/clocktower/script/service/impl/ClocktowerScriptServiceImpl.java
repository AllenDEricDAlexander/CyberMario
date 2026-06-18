package top.egon.mario.clocktower.script.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerJinxRuleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerTermResponse;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerJinxRuleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerScriptRepository;
import top.egon.mario.clocktower.script.service.ClocktowerScriptService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerScriptServiceImpl implements ClocktowerScriptService {

    private static final List<ClocktowerTermResponse> PHASE_ONE_TERMS = List.of(
            new ClocktowerTermResponse("提名", "ACTION", "白天阶段玩家选择处决候选人的公开动作。", null),
            new ClocktowerTermResponse("死亡投票", "VOTE", "死亡玩家通常仍保留一次投票机会。", null),
            new ClocktowerTermResponse("魔典", "STORYTELLER", "说书人看到的完整角色、标记和事件状态。", null)
    );

    private final ClocktowerScriptRepository scriptRepository;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerNightOrderRepository nightOrderRepository;
    private final ClocktowerJinxRuleRepository jinxRuleRepository;

    @Override
    public List<ClocktowerScriptResponse> listScripts() {
        return scriptRepository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc().stream()
                .map(ClocktowerScriptResponse::from)
                .toList();
    }

    @Override
    public ClocktowerScriptResponse getScript(ClocktowerScriptCode scriptCode) {
        return scriptRepository.findByScriptCodeAndDeletedFalse(scriptCode)
                .map(ClocktowerScriptResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("CLOCKTOWER_SCRIPT_NOT_FOUND"));
    }

    @Override
    public List<ClocktowerRoleResponse> listRoles(ClocktowerScriptCode scriptCode, String roleType, Boolean enabled) {
        ClocktowerRoleType parsedRoleType = StringUtils.hasText(roleType) ? ClocktowerRoleType.fromJson(roleType) : null;
        List<ClocktowerRolePo> roles;
        if (parsedRoleType != null && enabled != null) {
            roles = roleRepository.findByScriptCodeAndRoleTypeAndEnabledAndDeletedFalseOrderBySortOrderAsc(
                    scriptCode, parsedRoleType, enabled);
        } else if (parsedRoleType != null) {
            roles = roleRepository.findByScriptCodeAndRoleTypeAndDeletedFalseOrderBySortOrderAsc(scriptCode, parsedRoleType);
        } else if (enabled != null) {
            roles = roleRepository.findByScriptCodeAndEnabledAndDeletedFalseOrderBySortOrderAsc(scriptCode, enabled);
        } else {
            roles = roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(scriptCode);
        }
        return roles.stream().map(ClocktowerRoleResponse::from).toList();
    }

    @Override
    public List<ClocktowerNightOrderResponse> nightOrder(ClocktowerScriptCode scriptCode, String nightType) {
        ClocktowerNightType parsedNightType = StringUtils.hasText(nightType) ? ClocktowerNightType.fromJson(nightType) : null;
        List<ClocktowerNightOrderPo> orders = parsedNightType != null
                ? nightOrderRepository.findByScriptCodeAndNightTypeAndDeletedFalseOrderBySortOrderAsc(scriptCode,
                parsedNightType)
                : nightOrderRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(scriptCode);
        return toNightOrderResponses(scriptCode, orders);
    }

    @Override
    public ClocktowerNightOrderGroupResponse groupedNightOrder(ClocktowerScriptCode scriptCode) {
        List<ClocktowerNightOrderResponse> orders = nightOrder(scriptCode, null);
        return new ClocktowerNightOrderGroupResponse(
                orders.stream()
                        .filter(order -> order.nightType() == ClocktowerNightType.FIRST_NIGHT)
                        .toList(),
                orders.stream()
                        .filter(order -> order.nightType() == ClocktowerNightType.OTHER_NIGHT)
                        .toList());
    }

    private List<ClocktowerNightOrderResponse> toNightOrderResponses(ClocktowerScriptCode scriptCode,
                                                                     List<ClocktowerNightOrderPo> orders) {
        Map<String, ClocktowerRolePo> roles = roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(scriptCode)
                .stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, Function.identity(), (left, right) -> left));
        return orders.stream()
                .map(order -> ClocktowerNightOrderResponse.from(order, roles.get(order.getRoleCode())))
                .toList();
    }

    @Override
    public List<ClocktowerTermResponse> terms(String keyword, String category) {
        return PHASE_ONE_TERMS.stream()
                .filter(term -> !StringUtils.hasText(keyword) || term.term().contains(keyword)
                        || term.description().contains(keyword))
                .filter(term -> !StringUtils.hasText(category) || term.category().equalsIgnoreCase(category))
                .toList();
    }

    @Override
    public List<ClocktowerJinxRuleResponse> jinxRules(String roleCode, String severity) {
        if (StringUtils.hasText(roleCode)) {
            return jinxRuleRepository.findByRoleCodeAndDeletedFalseOrderByIdAsc(roleCode).stream()
                    .filter(rule -> !StringUtils.hasText(severity) || rule.getSeverity().equalsIgnoreCase(severity))
                    .map(ClocktowerJinxRuleResponse::from)
                    .toList();
        }
        if (StringUtils.hasText(severity)) {
            return jinxRuleRepository.findBySeverityAndDeletedFalseOrderByIdAsc(severity).stream()
                    .map(ClocktowerJinxRuleResponse::from)
                    .toList();
        }
        return jinxRuleRepository.findByDeletedFalseOrderByIdAsc().stream()
                .map(ClocktowerJinxRuleResponse::from)
                .toList();
    }
}
