package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

import java.util.Map;

public interface RoleMetadataProvider {

    Map<String, ClocktowerRoleType> roleTypes();
}
