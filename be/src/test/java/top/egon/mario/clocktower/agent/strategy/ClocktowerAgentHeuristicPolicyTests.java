package top.egon.mario.clocktower.agent.strategy;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.strategy.troublebrewing.TroubleBrewingBluffPlanner;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerAgentHeuristicPolicyTests {

    @Test
    void goodAgentWithPrivateInfoSpeaksOnMicTurnAndDoesNotExposeRawHiddenMarkers() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(goodView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(privateInfo(Map.of("infoType", "EMPATH", "evilCount", 1))),
                        List.of()),
                balancedProfile(70), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        AgentIntent.PublicSpeech speech = (AgentIntent.PublicSpeech) decision.intent();
        assertThat(speech.content()).contains("我这边有信息");
        assertThat(speech.content()).doesNotContain("evilCount");
        assertThat(speech.content().length()).isBetween(10, 180);
        assertThat(decision.reasoningSummary()).contains("private info");
    }

    @Test
    void lowTalkativeGoodAgentPassesWhenNoUsefulContentExists() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(goodView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(),
                        List.of()),
                balancedProfile(20), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.Pass.class);
        assertThat(decision.reasoningSummary()).contains("low talkativeness");
    }

    @Test
    void evilAgentUsesBluffPlanInsteadOfTrueRoleOnMicTurn() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(evilView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(memory("BLUFF_PLAN", Map.of(
                                "claimRoleCode", "CHEF",
                                "fakeInfo", Map.of("chefNumber", 1),
                                "pushTargets", List.of(2L))))),
                balancedProfile(80), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        AgentIntent.PublicSpeech speech = (AgentIntent.PublicSpeech) decision.intent();
        assertThat(speech.content()).contains("CHEF");
        assertThat(speech.content()).doesNotContain("SPY");
        assertThat(decision.diagnostics()).containsEntry("alignment", "EVIL");
    }

    @Test
    void nominationChoosesEligibleTargetAndDeadAgentDoesNotNominate() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision aliveDecision = policy.decide(context(goodView(
                        List.of(nominateIntent(List.of(2L, 3L))),
                        List.of(),
                        List.of(memory("SUSPICION_SCORE", Map.of("score", 90), 2L))),
                balancedProfile(50), "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(aliveDecision.intent()).isInstanceOf(AgentIntent.Nominate.class);
        assertThat(((AgentIntent.Nominate) aliveDecision.intent()).targetGameSeatId()).isEqualTo(2L);

        AgentPrivateView deadView = view("GOOD", "EMPATH", "DEAD",
                false, List.of(nominateIntent(List.of(2L))), List.of(), List.of(), Map.of());
        AgentDecision deadDecision = policy.decide(context(deadView, balancedProfile(50),
                "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(deadDecision.intent()).isInstanceOf(AgentIntent.Noop.class);
        assertThat(deadDecision.reasoningSummary()).contains("dead");
    }

    @Test
    void evilNominationAvoidsDemonWhenNonDemonTargetExists() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView evil = evilView(List.of(nominateIntent(List.of(2L, 3L, 4L))),
                List.of(memory("SUSPICION_SCORE", Map.of("score", 70), 2L)));
        AgentDecision decision = policy.decide(context(evil, balancedProfile(80),
                "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.Nominate.class);
        assertThat(((AgentIntent.Nominate) decision.intent()).targetGameSeatId()).isEqualTo(2L);
    }

    @Test
    void deadAgentUsesYesVoteOnlyAboveDeadVoteThreshold() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView weakDead = view("GOOD", "CHEF", "DEAD", true,
                List.of(voteIntent(99L, 2L, true), voteIntent(99L, 2L, false)),
                List.of(memory("SUSPICION_SCORE", Map.of("score", 70), 2L)), List.of(), Map.of());

        AgentDecision weakDecision = policy.decide(context(weakDead, balancedProfile(50),
                "VOTE_WINDOW_OPENED", Map.of("nominationId", 99L)));
        assertThat(weakDecision.intent()).isInstanceOf(AgentIntent.Vote.class);
        assertThat(((AgentIntent.Vote) weakDecision.intent()).vote()).isFalse();

        AgentPrivateView strongDead = view("GOOD", "CHEF", "DEAD", true,
                List.of(voteIntent(99L, 2L, true), voteIntent(99L, 2L, false)),
                List.of(memory("SUSPICION_SCORE", Map.of("score", 90), 2L)), List.of(), Map.of());

        AgentDecision strongDecision = policy.decide(context(strongDead, balancedProfile(50),
                "VOTE_WINDOW_OPENED", Map.of("nominationId", 99L)));
        assertThat(strongDecision.intent()).isInstanceOf(AgentIntent.Vote.class);
        assertThat(((AgentIntent.Vote) strongDecision.intent()).vote()).isTrue();
    }

    @Test
    void nightChoiceUsesRoleHeuristicAndFallsBackToLegalTarget() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView monk = view("GOOD", "MONK", "ALIVE", true,
                List.of(nightIntent(501L, "MONK", List.of(1L, 2L, 3L))),
                List.of(memory("TRUST_SCORE", Map.of("score", 85), 3L)), List.of(), Map.of());

        AgentDecision decision = policy.decide(context(monk, balancedProfile(50),
                "NIGHT_TASK_OPENED", Map.of("taskId", 501L)));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.NightChoice.class);
        AgentIntent.NightChoice choice = (AgentIntent.NightChoice) decision.intent();
        assertThat(choice.taskId()).isEqualTo(501L);
        assertThat(choice.targetGameSeatIds()).containsExactly(3L);
    }

    private HeuristicAgentPolicy policy() {
        TroubleBrewingBluffPlanner bluffPlanner = new TroubleBrewingBluffPlanner(null, null);
        return new HeuristicAgentPolicy(new AgentSpeechPlanner(bluffPlanner),
                new AgentNominationPlanner(), new AgentVotePlanner(), new AgentNightChoicePlanner());
    }

    private AgentDecisionContext context(AgentPrivateView view, ClocktowerAgentProfilePo profile,
                                         String triggerType, Map<String, Object> metadata) {
        return new AgentDecisionContext(view, profile, view.legalIntents(), triggerType, metadata, Map.of());
    }

    private ClocktowerAgentProfilePo balancedProfile(int talkativeness) {
        ClocktowerAgentProfilePo profile = new ClocktowerAgentProfilePo();
        profile.setName("balanced-test");
        profile.setTalkativeness(talkativeness);
        profile.setAggression(50);
        profile.setRiskTolerance(50);
        profile.setDeceptionLevel(50);
        return profile;
    }

    private AgentPrivateView goodView(List<AgentLegalIntentView> intents,
                                      List<AgentPrivateInfoView> privateInfos,
                                      List<AgentMemoryView> memories) {
        return view("GOOD", "EMPATH", "ALIVE", true, intents, memories, privateInfos, Map.of());
    }

    private AgentPrivateView evilView(List<AgentLegalIntentView> intents, List<AgentMemoryView> memories) {
        return view("EVIL", "SPY", "ALIVE", true, intents, memories, List.of(),
                Map.of("evilTeam", List.of(Map.of(
                        "gameSeatId", 4L,
                        "seatNo", 4,
                        "roleCode", "IMP",
                        "roleType", "DEMON",
                        "isDemon", true))));
    }

    private AgentPrivateView view(String alignment, String roleCode, String lifeStatus, boolean hasDeadVote,
                                  List<AgentLegalIntentView> intents, List<AgentMemoryView> memories,
                                  List<AgentPrivateInfoView> privateInfos, Map<String, Object> roleContext) {
        return new AgentPrivateView(1L, 10L, 1L, 1, "DAY", 1, 1, roleCode, roleCode,
                alignment, "SPY".equals(roleCode) ? "MINION" : "TOWNSFOLK", lifeStatus, lifeStatus,
                hasDeadVote, publicSeats(), List.of(), List.of(), privateInfos, memories, intents, roleContext);
    }

    private List<AgentPublicSeatView> publicSeats() {
        return List.of(
                new AgentPublicSeatView(1L, 1, "Agent", null, null, null, null,
                        "ALIVE", true, false, "AGENT", true, "ACTIVE"),
                new AgentPublicSeatView(2L, 2, "Player 2", null, null, null, null,
                        "ALIVE", true, false, "HUMAN", false, "ACTIVE"),
                new AgentPublicSeatView(3L, 3, "Player 3", null, null, null, null,
                        "ALIVE", true, false, "HUMAN", false, "ACTIVE"),
                new AgentPublicSeatView(4L, 4, "Player 4", null, null, null, null,
                        "ALIVE", true, false, "AGENT", true, "ACTIVE"));
    }

    private AgentLegalIntentView publicSpeech() {
        return new AgentLegalIntentView("PUBLIC_SPEECH", null, null, null, Map.of());
    }

    private AgentLegalIntentView passIntent() {
        return new AgentLegalIntentView("PASS", null, null, null, Map.of("passType", "MIC_TURN"));
    }

    private AgentLegalIntentView nominateIntent(List<Long> targets) {
        return new AgentLegalIntentView("NOMINATE", null, null, null,
                Map.of("eligibleTargetGameSeatIds", targets));
    }

    private AgentLegalIntentView voteIntent(Long nominationId, Long nomineeSeatId, boolean vote) {
        return new AgentLegalIntentView("VOTE", null, nominationId, vote,
                Map.of("nomineeGameSeatId", nomineeSeatId));
    }

    private AgentLegalIntentView nightIntent(Long taskId, String roleCode, List<Long> targets) {
        return new AgentLegalIntentView("NIGHT_CHOICE", taskId, null, null,
                Map.of("roleCode", roleCode, "taskType", "TARGET",
                        "legalTargetGameSeatIds", targets));
    }

    private AgentPrivateInfoView privateInfo(Map<String, Object> payload) {
        return new AgentPrivateInfoView(700L, 7L, "EMPATH", "RECEIVE_INFO", payload, Instant.now());
    }

    private AgentMemoryView memory(String type, Map<String, Object> content) {
        return memory(type, content, null);
    }

    private AgentMemoryView memory(String type, Map<String, Object> content, Long subjectGameSeatId) {
        return new AgentMemoryView(900L + (subjectGameSeatId == null ? 0L : subjectGameSeatId),
                null, null, type, subjectGameSeatId, content, 80, 1, 1, Instant.now());
    }
}
