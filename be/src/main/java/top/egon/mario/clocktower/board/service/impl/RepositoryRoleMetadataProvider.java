package top.egon.mario.clocktower.board.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RepositoryRoleMetadataProvider implements RoleMetadataProvider {

    private final ClocktowerRoleRepository roleRepository;

    @Override
    public List<ClocktowerRoleSummaryResponse> roles(ClocktowerScriptCode scriptCode) {
        return roleRepository.findByScriptCodeAndEnabledAndDeletedFalseOrderBySortOrderAsc(scriptCode, true).stream()
                .map(ClocktowerRoleSummaryResponse::from)
                .toList();
    }

    @Override
    public List<ClocktowerRoleSummaryResponse> enabledRoles(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByRoleCodeInAndEnabledAndDeletedFalse(roleCodes, true).stream()
                .map(ClocktowerRoleSummaryResponse::from)
                .toList();
    }
}
