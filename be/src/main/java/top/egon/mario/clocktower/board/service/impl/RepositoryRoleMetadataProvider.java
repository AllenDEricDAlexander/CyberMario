package top.egon.mario.clocktower.board.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RepositoryRoleMetadataProvider implements RoleMetadataProvider {

    private final ClocktowerRoleRepository roleRepository;

    @Override
    public Map<String, ClocktowerRoleType> roleTypes() {
        return roleRepository.findAll().stream()
                .filter(role -> !role.isDeleted())
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, ClocktowerRolePo::getRoleType,
                        (left, right) -> left));
    }

    @Override
    public List<String> roleCodes(ClocktowerRoleType roleType) {
        return roleRepository.findAll().stream()
                .filter(role -> !role.isDeleted() && role.getRoleType() == roleType)
                .map(ClocktowerRolePo::getRoleCode)
                .sorted()
                .toList();
    }
}
