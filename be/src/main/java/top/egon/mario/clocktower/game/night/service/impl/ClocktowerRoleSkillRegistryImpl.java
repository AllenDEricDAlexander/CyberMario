package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClocktowerRoleSkillRegistryImpl implements ClocktowerRoleSkillRegistry {

    private final Map<String, RoleSkill> skillByRoleCode;

    public ClocktowerRoleSkillRegistryImpl(List<RoleSkill> skills) {
        this.skillByRoleCode = skills.stream()
                .collect(Collectors.toMap(RoleSkill::roleCode, Function.identity(), (left, right) -> left));
    }

    @Override
    public Optional<RoleSkill> find(String roleCode) {
        return Optional.ofNullable(skillByRoleCode.get(roleCode));
    }
}
