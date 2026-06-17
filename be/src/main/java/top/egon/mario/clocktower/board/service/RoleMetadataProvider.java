package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

import java.util.List;
import java.util.Map;

public interface RoleMetadataProvider {

    Map<String, ClocktowerRoleType> roleTypes();

    default List<String> roleCodes(ClocktowerRoleType roleType) {
        return roleTypes().entrySet().stream()
                .filter(entry -> entry.getValue() == roleType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
