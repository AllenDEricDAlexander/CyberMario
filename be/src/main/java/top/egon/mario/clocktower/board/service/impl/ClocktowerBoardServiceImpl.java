package top.egon.mario.clocktower.board.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRoleTypeCountResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerScoreResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.engine.BoardCandidateFact;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.RuleDecisionCollector;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerBoardServiceImpl implements ClocktowerBoardService {

    private final RoleMetadataProvider roleMetadataProvider;
    private final ClocktowerRuleEngine ruleEngine;

    @Override
    public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
        Map<String, ClocktowerRoleType> roleTypes = roleMetadataProvider.roleTypes();
        ClocktowerRoleTypeCountResponse typeCounts = countRoleTypes(request.roleCodes(), roleTypes);
        BoardCandidateFact fact = new BoardCandidateFact(request.scriptCode(), request.playerCount(), request.roleCodes(),
                typeCounts.townsfolk(), typeCounts.outsider(), typeCounts.minion(), typeCounts.demon());
        RuleDecisionCollector collector = ruleEngine.validateBoard(fact);
        List<ClocktowerRuleViolationResponse> issues = collector.violations().stream()
                .map(ClocktowerRuleViolationResponse::from)
                .toList();
        List<ClocktowerScoreResponse> scores = collector.scores().stream()
                .map(ClocktowerScoreResponse::from)
                .toList();
        return new BoardValidationResponse(issues.isEmpty(), typeCounts, issues, scores);
    }

    private ClocktowerRoleTypeCountResponse countRoleTypes(List<String> roleCodes, Map<String, ClocktowerRoleType> roleTypes) {
        int townsfolk = 0;
        int outsider = 0;
        int minion = 0;
        int demon = 0;
        int traveler = 0;
        int fabled = 0;
        for (String roleCode : roleCodes) {
            ClocktowerRoleType roleType = roleTypes.get(roleCode);
            if (roleType == ClocktowerRoleType.TOWNSFOLK) {
                townsfolk++;
            } else if (roleType == ClocktowerRoleType.OUTSIDER) {
                outsider++;
            } else if (roleType == ClocktowerRoleType.MINION) {
                minion++;
            } else if (roleType == ClocktowerRoleType.DEMON) {
                demon++;
            } else if (roleType == ClocktowerRoleType.TRAVELER) {
                traveler++;
            } else if (roleType == ClocktowerRoleType.FABLED) {
                fabled++;
            }
        }
        return new ClocktowerRoleTypeCountResponse(townsfolk, outsider, minion, demon, traveler, fabled);
    }
}
