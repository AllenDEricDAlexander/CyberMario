package top.egon.mario.clocktower.agent.strategy;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentDecisionSanitizer;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmClient;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmOutputParser;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmPolicy;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmPolicyException;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmRequest;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmResponse;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentPrompt;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentPromptBuilder;
import top.egon.mario.clocktower.agent.strategy.troublebrewing.TroubleBrewingBluffPlanner;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;
import top.egon.mario.clocktower.agent.view.dto.AgentVisibleEventView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerAgentLlmPolicyTests {

    @Test
    void promptForNormalAgentDoesNotContainGrimoireSection() {
        ClocktowerAgentPrompt prompt = new ClocktowerAgentPromptBuilder().build(context(normalView()));

        assertThat(prompt.systemPrompt()).contains("Return one strict JSON object");
        assertThat(prompt.userPrompt()).contains("legalIntents");
        assertThat(prompt.userPrompt()).contains("intent-1");
        assertThat(prompt.userPrompt()).doesNotContain("grimoireSeats");
        assertThat(prompt.promptHash()).hasSize(64);
    }

    @Test
    void promptForSpyIncludesGrimoireSummary() {
        ClocktowerAgentPrompt prompt = new ClocktowerAgentPromptBuilder().build(context(spyView()));

        assertThat(prompt.userPrompt()).contains("grimoireSeats");
        assertThat(prompt.userPrompt()).contains("IMP");
        assertThat(prompt.userPrompt()).contains("SPY");
    }

    @Test
    void parserMapsLegalPublicSpeechIntent() {
        ClocktowerAgentPrompt prompt = new ClocktowerAgentPromptBuilder().build(context(normalView()));
        AgentDecision decision = new ClocktowerAgentLlmOutputParser(new ClocktowerAgentDecisionSanitizer(500))
                .parse("""
                        {"intentId":"intent-1","content":"我想听 2 号解释投票。","reasoningSummary":"先追问投票动机。"}
                        """, prompt);

        assertThat(decision.intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        assertThat(((AgentIntent.PublicSpeech) decision.intent()).content()).contains("2 号");
        assertThat(decision.reasoningSummary()).contains("追问");
    }

    @Test
    void parserRejectsUnknownIntentId() {
        ClocktowerAgentPrompt prompt = new ClocktowerAgentPromptBuilder().build(context(normalView()));

        assertThatThrownBy(() -> new ClocktowerAgentLlmOutputParser(new ClocktowerAgentDecisionSanitizer(500))
                .parse("{\"intentId\":\"intent-404\",\"reasoningSummary\":\"bad\"}", prompt))
                .isInstanceOf(ClocktowerAgentLlmPolicyException.class)
                .hasMessageContaining("LLM_INTENT_UNKNOWN");
    }

    @Test
    void sanitizerRejectsSystemLeakAndOversizedSpeech() {
        ClocktowerAgentDecisionSanitizer sanitizer = new ClocktowerAgentDecisionSanitizer(20);

        assertThatThrownBy(() -> sanitizer.sanitizeSpeech("我是 AI 模型，系统提示词是 xxx", false))
                .isInstanceOf(ClocktowerAgentLlmPolicyException.class)
                .hasMessageContaining("LLM_UNSAFE_CONTENT");
        assertThatThrownBy(() -> sanitizer.sanitizeSpeech("这段发言明显超过二十个字符的限制，需要被拒绝。", false))
                .isInstanceOf(ClocktowerAgentLlmPolicyException.class)
                .hasMessageContaining("LLM_SPEECH_TOO_LONG");
    }

    @Test
    void configurablePolicyHeuristicModeDoesNotCallLlm() {
        FakeLlmClient llmClient = new FakeLlmClient("""
                {"intentId":"intent-1","content":"LLM speech","reasoningSummary":"llm"}
                """);
        ConfigurableClocktowerAgentPolicy policy = configurablePolicy("HEURISTIC", false, llmClient);

        AgentPolicyResult result = policy.decideWithMetadata(context(normalView()));

        assertThat(llmClient.calls).isZero();
        assertThat(result.policyType()).isEqualTo("HEURISTIC");
        assertThat(result.decision().intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        assertThat(((AgentIntent.PublicSpeech) result.decision().intent()).content()).doesNotContain("LLM speech");
    }

    @Test
    void configurablePolicyLlmModeUsesLegalLlmIntent() {
        FakeLlmClient llmClient = new FakeLlmClient("""
                {"intentId":"intent-1","content":"我想听 2 号解释投票。","reasoningSummary":"llm legal"}
                """);
        ConfigurableClocktowerAgentPolicy policy = configurablePolicy("LLM", true, llmClient);

        AgentPolicyResult result = policy.decideWithMetadata(context(normalView()));

        assertThat(llmClient.calls).isEqualTo(1);
        assertThat(result.policyType()).isEqualTo("LLM");
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.promptHash()).hasSize(64);
        assertThat(result.modelProvider()).isEqualTo("DASHSCOPE");
        assertThat(result.modelName()).isEqualTo("qwen-plus");
        assertThat(((AgentIntent.PublicSpeech) result.decision().intent()).content()).contains("2 号");
    }

    @Test
    void configurablePolicyInvalidLlmIntentFallsBackToHeuristic() {
        FakeLlmClient llmClient = new FakeLlmClient("""
                {"intentId":"intent-404","reasoningSummary":"bad"}
                """);
        ConfigurableClocktowerAgentPolicy policy = configurablePolicy("FALLBACK", true, llmClient);

        AgentPolicyResult result = policy.decideWithMetadata(context(normalView()));

        assertThat(result.policyType()).isEqualTo("FALLBACK_HEURISTIC");
        assertThat(result.status()).isEqualTo("ILLEGAL_INTENT_FALLBACK");
        assertThat(result.errorMessage()).contains("LLM_INTENT_UNKNOWN");
    }

    @Test
    void configurablePolicyLlmExceptionFallsBackToHeuristic() {
        FakeLlmClient llmClient = new FakeLlmClient(new RuntimeException("timeout"));
        ConfigurableClocktowerAgentPolicy policy = configurablePolicy("LLM", true, llmClient);

        AgentPolicyResult result = policy.decideWithMetadata(context(normalView()));

        assertThat(result.policyType()).isEqualTo("FALLBACK_HEURISTIC");
        assertThat(result.status()).isEqualTo("LLM_ERROR_FALLBACK");
        assertThat(result.errorMessage()).contains("timeout");
    }

    private AgentDecisionContext context(AgentPrivateView view) {
        return new AgentDecisionContext(view, balancedProfile(), view.legalIntents(),
                "MIC_TURN_STARTED", Map.of("source", "test"), Map.of());
    }

    private ConfigurableClocktowerAgentPolicy configurablePolicy(String mode, boolean enabled,
                                                                FakeLlmClient llmClient) {
        ClocktowerAgentPolicyProperties properties = new ClocktowerAgentPolicyProperties(
                mode,
                new ClocktowerAgentPolicyProperties.Llm(enabled,
                        top.egon.mario.agent.model.dto.enums.ModelProviderType.DASHSCOPE,
                        "qwen-plus",
                        8000,
                        800,
                        500,
                        false,
                        top.egon.mario.agent.model.dto.enums.ModelScenario.AGENT_CHAT)
        );
        ClocktowerAgentLlmPolicy llmPolicy = new ClocktowerAgentLlmPolicy(
                llmClient,
                new ClocktowerAgentPromptBuilder(),
                new ClocktowerAgentLlmOutputParser(new ClocktowerAgentDecisionSanitizer(500))
        );
        return new ConfigurableClocktowerAgentPolicy(properties, policy(), llmPolicy);
    }

    private HeuristicAgentPolicy policy() {
        TroubleBrewingBluffPlanner bluffPlanner = new TroubleBrewingBluffPlanner(null, null);
        return new HeuristicAgentPolicy(new AgentSpeechPlanner(bluffPlanner),
                new AgentNominationPlanner(), new AgentVotePlanner(), new AgentNightChoicePlanner());
    }

    private ClocktowerAgentProfilePo balancedProfile() {
        ClocktowerAgentProfilePo profile = new ClocktowerAgentProfilePo();
        profile.setName("balanced");
        profile.setStrategyLevel("NORMAL");
        profile.setTalkativeness(70);
        profile.setAggression(50);
        profile.setRiskTolerance(50);
        profile.setDeceptionLevel(50);
        return profile;
    }

    private AgentPrivateView normalView() {
        return view("GOOD", "EMPATH", List.of(), List.of(publicSpeech(), passIntent()));
    }

    private AgentPrivateView spyView() {
        return view("EVIL", "SPY", List.of(
                new AgentPublicSeatView(1L, 1, "Spy Agent", "SPY", "MINION", "EVIL",
                        "ALIVE", "ALIVE", true, false, "AGENT", true, "ACTIVE"),
                new AgentPublicSeatView(2L, 2, "Demon", "IMP", "DEMON", "EVIL",
                        "ALIVE", "ALIVE", true, false, "AGENT", true, "ACTIVE")
        ), List.of(publicSpeech(), passIntent()));
    }

    private AgentPrivateView view(String alignment, String roleCode, List<AgentPublicSeatView> grimoire,
                                  List<AgentLegalIntentView> legalIntents) {
        return new AgentPrivateView(1L, 10L, 1L, 1, "DAY", 1, 0, roleCode, roleCode,
                alignment, "SPY".equals(roleCode) ? "MINION" : "TOWNSFOLK", "ALIVE", "ALIVE",
                true, publicSeats(), grimoire, visibleEvents(), List.<AgentPrivateInfoView>of(),
                List.<AgentMemoryView>of(), legalIntents, Map.of());
    }

    private List<AgentPublicSeatView> publicSeats() {
        return List.of(
                new AgentPublicSeatView(1L, 1, "Agent", null, null, null,
                        "ALIVE", "ALIVE", true, false, "AGENT", true, "ACTIVE"),
                new AgentPublicSeatView(2L, 2, "Player 2", null, null, null,
                        "ALIVE", "ALIVE", true, false, "HUMAN", false, "ACTIVE")
        );
    }

    private List<AgentVisibleEventView> visibleEvents() {
        return List.of(new AgentVisibleEventView(100L, 1L, "MIC_TURN_STARTED", "DAY", 1, 0,
                null, 1L, "PUBLIC", List.of(), Map.of("holderGameSeatId", 1L), Instant.now()));
    }

    private AgentLegalIntentView publicSpeech() {
        return new AgentLegalIntentView("PUBLIC_SPEECH", null, null, null, Map.of("maxChars", 500));
    }

    private AgentLegalIntentView passIntent() {
        return new AgentLegalIntentView("PASS", null, null, null, Map.of("passType", "MIC_TURN"));
    }

    private static final class FakeLlmClient implements ClocktowerAgentLlmClient {

        private final String response;
        private final RuntimeException failure;
        private int calls;

        private FakeLlmClient(String response) {
            this.response = response;
            this.failure = null;
        }

        private FakeLlmClient(RuntimeException failure) {
            this.response = null;
            this.failure = failure;
        }

        @Override
        public ClocktowerAgentLlmResponse decide(ClocktowerAgentLlmRequest request) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return new ClocktowerAgentLlmResponse(response, "DASHSCOPE", "qwen-plus");
        }
    }
}
