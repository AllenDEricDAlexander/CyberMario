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
                        summary(scriptCode, "WASHERWOMAN", "洗衣妇", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个镇民。", 1, true, false, false),
                        summary(scriptCode, "LIBRARIAN", "图书管理员", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个外来者，或者没有外来者。", 2, true, false, false),
                        summary(scriptCode, "INVESTIGATOR", "调查员", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个爪牙。", 2, true, false, false),
                        summary(scriptCode, "FORTUNETELLER", "占卜师", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "每晚选择两名玩家，得知其中是否有恶魔，且有干扰项。", 3, true, true, false),
                        summary(scriptCode, "UNDERTAKER", "送葬者", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "每晚得知今天白天死于处决的角色。", 2, false, true, false),
                        summary(scriptCode, "RAVENKEEPER", "守鸦人", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "如果你在夜晚死亡，你会被唤醒并得知一名玩家的角色。", 3, false, true, false),
                        summary(scriptCode, "VIRGIN", "贞洁者", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "第一次被镇民提名时，提名者立刻被处决。", 2, false, false, false),
                        summary(scriptCode, "SLAYER", "猎手", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "白天限一次选择玩家，如果他是恶魔，他死亡。", 2, false, false, false),
                        summary(scriptCode, "SOLDIER", "士兵", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "你免受恶魔伤害。", 1, false, false, false),
                        summary(scriptCode, "MAYOR", "镇长", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD, "三人存活且无人被处决时善良获胜，夜晚死亡可能转移。", 3, false, false, false),
                        summary(scriptCode, "EMPATH", "共情者", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                                "每晚得知邻近存活玩家中有几名邪恶玩家。", 2, true, true, false),
                        summary(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                                "首夜得知相邻邪恶玩家有多少对。", 1, true, false, false),
                        summary(scriptCode, "MONK", "僧侣", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                                "每晚保护一名玩家免受恶魔伤害。", 1, false, true, false),
                        summary(scriptCode, "BUTLER", "管家", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                                "每晚选择主人，明天只能在主人投票时投票。", 1, true, true, false),
                        summary(scriptCode, "DRUNK", "酒鬼", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                                "你不知道自己是酒鬼，以为自己是镇民。", 2, false, false, false),
                        summary(scriptCode, "RECLUSE", "陌客", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                                "你可能被当作邪恶阵营、爪牙或恶魔。", 2, false, false, false),
                        summary(scriptCode, "SAINT", "圣徒", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                                "如果你死于处决，你的阵营落败。", 1, false, false, false),
                        summary(scriptCode, "POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                                "每晚选择一名玩家：他中毒。", 2, true, true, false),
                        summary(scriptCode, "SPY", "间谍", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                                "每晚看到魔典，且可能被当作善良、镇民或外来者。", 3, true, true, false),
                        summary(scriptCode, "SCARLETWOMAN", "红唇女郎", ClocktowerRoleType.MINION,
                                ClocktowerAlignment.EVIL, "恶魔死亡时，如果存活玩家够多，你变成恶魔。", 2, false, true, false),
                        summary(scriptCode, "BARON", "男爵", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                                "有额外的外来者在场。", 3, false, false, true),
                        summary(scriptCode, "IMP", "小恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL,
                                "每晚选择一名玩家：他死亡。如果杀死自己，一名爪牙变成小恶魔。", 2, false, true, false));
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

    private static ClocktowerRoleSummaryResponse summary(ClocktowerScriptCode scriptCode, String roleCode,
                                                         String roleName, ClocktowerRoleType roleType,
                                                         ClocktowerAlignment alignment, String abilityText,
                                                         int complexity, boolean firstNight, boolean otherNight,
                                                         boolean setupModifier) {
        return new ClocktowerRoleSummaryResponse(scriptCode, roleCode, roleName, roleType, alignment, abilityText,
                complexity, firstNight, otherNight, setupModifier);
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
