package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.Optional;

public interface ClocktowerRoleSkillRegistry {

    Optional<RoleSkill> find(String roleCode);
}
