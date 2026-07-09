package top.egon.mario.clocktower.agent.decision;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.decision.po.ClocktowerAgentDecisionPo;
import top.egon.mario.clocktower.agent.decision.repository.ClocktowerAgentDecisionRepository;
import top.egon.mario.clocktower.agent.decision.service.ClocktowerAgentDecisionAuditCommand;
import top.egon.mario.clocktower.agent.decision.service.ClocktowerAgentDecisionAuditService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.worker.runner.enabled=false"
})
class ClocktowerAgentDecisionAuditServiceTests {

    @Autowired
    private ClocktowerAgentDecisionAuditService auditService;

    @Autowired
    private ClocktowerAgentDecisionRepository decisionRepository;

    @Test
    @Transactional
    void writePersistsDecisionAuditWithoutFullPrompt() {
        ClocktowerAgentDecisionPo saved = auditService.write(command(99001L, 99101L, 99201L, 99301L,
                "LLM", "ACCEPTED", null));

        ClocktowerAgentDecisionPo reloaded = decisionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getGameId()).isEqualTo(99001L);
        assertThat(reloaded.getAgentInstanceId()).isEqualTo(99101L);
        assertThat(reloaded.getGameSeatId()).isEqualTo(99201L);
        assertThat(reloaded.getTriggerTaskId()).isEqualTo(99301L);
        assertThat(reloaded.getPhase()).isEqualTo("DAY");
        assertThat(reloaded.getDayNo()).isEqualTo(1);
        assertThat(reloaded.getNightNo()).isZero();
        assertThat(reloaded.getDecisionType()).isEqualTo("PUBLIC_SPEECH");
        assertThat(reloaded.getPolicyType()).isEqualTo("LLM");
        assertThat(reloaded.getLegalIntentsJson()).contains("PUBLIC_SPEECH");
        assertThat(reloaded.getSelectedIntentJson()).contains("hello");
        assertThat(reloaded.getReasoningSummary()).isEqualTo("legal speech");
        assertThat(reloaded.getModelProvider()).isEqualTo("DASHSCOPE");
        assertThat(reloaded.getModelName()).isEqualTo("qwen-plus");
        assertThat(reloaded.getPromptHash()).isEqualTo("hash-1");
        assertThat(reloaded.getStatus()).isEqualTo("ACCEPTED");
        assertThat(reloaded.getErrorMessage()).isNull();
        assertThat(reloaded.getMetadataJson()).contains("configuredPolicy");
        assertThat(reloaded.getMetadataJson()).doesNotContain("systemPrompt");
        assertThat(reloaded.getMetadataJson()).doesNotContain("userPrompt");
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.isDeleted()).isFalse();
    }

    @Test
    @Transactional
    void repositoryFindsAgentDecisionHistoryNewestFirst() {
        ClocktowerAgentDecisionPo first = auditService.write(command(99002L, 99102L, 99202L, 99302L,
                "HEURISTIC", "ACCEPTED", null));
        ClocktowerAgentDecisionPo second = auditService.write(command(99002L, 99102L, 99202L, 99303L,
                "FALLBACK_HEURISTIC", "LLM_ERROR_FALLBACK", "timeout"));

        List<ClocktowerAgentDecisionPo> history = decisionRepository
                .findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtDescIdDesc(99002L, 99102L);
        List<ClocktowerAgentDecisionPo> taskRows = decisionRepository
                .findByTriggerTaskIdAndDeletedFalseOrderByIdAsc(99303L);

        assertThat(history).extracting(ClocktowerAgentDecisionPo::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(taskRows).extracting(ClocktowerAgentDecisionPo::getId)
                .containsExactly(second.getId());
        assertThat(history.getFirst().getStatus()).isEqualTo("LLM_ERROR_FALLBACK");
        assertThat(history.getFirst().getErrorMessage()).isEqualTo("timeout");
    }

    private ClocktowerAgentDecisionAuditCommand command(Long gameId, Long agentInstanceId, Long gameSeatId,
                                                        Long triggerTaskId, String policyType, String status,
                                                        String errorMessage) {
        return new ClocktowerAgentDecisionAuditCommand(
                gameId,
                agentInstanceId,
                gameSeatId,
                triggerTaskId,
                "DAY",
                1,
                0,
                "PUBLIC_SPEECH",
                policyType,
                List.of(Map.of("intentType", "PUBLIC_SPEECH")),
                Map.of("intentType", "PUBLIC_SPEECH", "content", "hello"),
                "legal speech",
                "DASHSCOPE",
                "qwen-plus",
                "hash-1",
                status,
                errorMessage,
                Map.of("configuredPolicy", policyType)
        );
    }
}
