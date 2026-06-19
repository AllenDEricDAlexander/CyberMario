package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface RoleMetadataProvider {

    List<ClocktowerRoleSummaryResponse> roles(ClocktowerScriptCode scriptCode);

    default List<ClocktowerRoleSummaryResponse> enabledRoles(Collection<String> roleCodes) {
        return List.of();
    }

    default Map<String, ClocktowerRoleType> roleTypes(ClocktowerScriptCode scriptCode) {
        return roles(scriptCode).stream()
                .collect(Collectors.toMap(ClocktowerRoleSummaryResponse::roleCode,
                        ClocktowerRoleSummaryResponse::roleType, (left, right) -> left));
    }

    default List<String> roleCodes(ClocktowerScriptCode scriptCode, ClocktowerRoleType roleType) {
        return roleTypes(scriptCode).entrySet().stream()
                .filter(entry -> entry.getValue() == roleType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    default List<ClocktowerRoleSummaryResponse> roleSummaries(ClocktowerScriptCode scriptCode,
                                                              Collection<String> roleCodes) {
        Map<String, ClocktowerRoleSummaryResponse> roles = roles(scriptCode).stream()
                .collect(Collectors.toMap(ClocktowerRoleSummaryResponse::roleCode, Function.identity(),
                        (left, right) -> left));
        return roleCodes.stream()
                .map(roleCode -> roles.getOrDefault(roleCode,
                        new ClocktowerRoleSummaryResponse(scriptCode, roleCode, roleCode, null, null)))
                .toList();
    }
}
