package top.egon.mario.clocktower.board;

import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.board.service.impl.ClocktowerBoardServiceImpl;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.engine.BoardCandidateFact;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.RuleDecisionCollector;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.Mockito.mock;

final class ClocktowerBoardTestFactory {

    private ClocktowerBoardTestFactory() {
    }

    static ClocktowerBoardService service() {
        RoleMetadataProvider provider = () -> Map.of(
                "EMPATH", ClocktowerRoleType.TOWNSFOLK,
                "CHEF", ClocktowerRoleType.TOWNSFOLK,
                "MONK", ClocktowerRoleType.TOWNSFOLK,
                "POISONER", ClocktowerRoleType.MINION,
                "IMP", ClocktowerRoleType.DEMON
        );
        return new ClocktowerBoardServiceImpl(provider, new TestClocktowerRuleEngine(),
                mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository.class),
                mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository.class),
                new ObjectMapper());
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
