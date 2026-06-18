package top.egon.mario.clocktower.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.board.service.impl.ClocktowerBoardServiceImpl;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.engine.BoardCandidateFact;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.RuleDecisionCollector;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;

import static org.mockito.Mockito.mock;

final class ClocktowerBoardTestFactory {

    private ClocktowerBoardTestFactory() {
    }

    static ClocktowerBoardService service() {
        RoleMetadataProvider provider = scriptCode -> {
            if (scriptCode == ClocktowerScriptCode.TROUBLE_BREWING) {
                return List.of(
                        summary(scriptCode, "EMPATH", "共情者", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
                        summary(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
                        summary(scriptCode, "MONK", "僧侣", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
                        summary(scriptCode, "POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL),
                        summary(scriptCode, "IMP", "小恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL));
            }
            return List.of(
                    summary(scriptCode, "BMR_TOWNSFOLK", "黯月镇民", ClocktowerRoleType.TOWNSFOLK,
                            ClocktowerAlignment.GOOD),
                    summary(scriptCode, "BMR_MINION", "黯月爪牙", ClocktowerRoleType.MINION,
                            ClocktowerAlignment.EVIL),
                    summary(scriptCode, "BMR_DEMON", "黯月恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL));
        };
        return new ClocktowerBoardServiceImpl(provider, new TestClocktowerRuleEngine(),
                mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository.class),
                mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository.class),
                new ObjectMapper());
    }

    static ClocktowerRuleEngine ruleEngine() {
        return new TestClocktowerRuleEngine();
    }

    private static ClocktowerRoleSummaryResponse summary(ClocktowerScriptCode scriptCode, String roleCode,
                                                         String roleName, ClocktowerRoleType roleType,
                                                         ClocktowerAlignment alignment) {
        return new ClocktowerRoleSummaryResponse(scriptCode, roleCode, roleName, roleType, alignment);
    }

    private static final class TestClocktowerRuleEngine extends ClocktowerRuleEngine {

        private TestClocktowerRuleEngine() {
            super(null);
        }

        @Override
        public RuleDecisionCollector validateBoard(BoardCandidateFact fact) {
            RuleDecisionCollector collector = new RuleDecisionCollector();
            if (fact.scriptCode().name().equals("TROUBLE_BREWING") && fact.playerCount() < 5) {
                collector.reject("BOARD_PLAYER_COUNT_TOO_LOW", "暗流涌动至少需要 5 名玩家。", "ERROR");
            }
            if (!fact.scriptCode().name().equals("TROUBLE_BREWING") && fact.playerCount() < 7) {
                collector.reject("BOARD_PLAYER_COUNT_TOO_LOW", "该剧本至少需要 7 名玩家。", "ERROR");
            }
            if (fact.roleCount() != fact.playerCount()) {
                collector.reject("BOARD_ROLE_COUNT_MISMATCH", "角色数量必须和玩家人数一致。", "ERROR");
            }
            if (fact.playerCount() == 5 && (fact.townsfolkCount() != 3 || fact.outsiderCount() != 0
                    || fact.minionCount() != 1 || fact.demonCount() != 1)) {
                collector.reject("BOARD_ROLE_TYPE_SHAPE_INVALID", "5 人局需要 3 镇民、1 爪牙、1 恶魔。", "ERROR");
            }
            return collector;
        }
    }
}
