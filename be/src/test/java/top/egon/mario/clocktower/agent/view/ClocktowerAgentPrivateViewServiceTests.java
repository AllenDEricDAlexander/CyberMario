package top.egon.mario.clocktower.agent.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.service.ClocktowerAgentPrivateViewService;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.worker.runner.enabled=false"
})
class ClocktowerAgentPrivateViewServiceTests {

    @Autowired
    private ClocktowerAgentPrivateViewService privateViewService;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentProfileRepository profileRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ClocktowerGameEventAppender eventAppender;

    @Autowired
    private ClocktowerGamePublicMicSessionRepository micSessionRepository;

    @Autowired
    private ClocktowerGameNightTaskRepository nightTaskRepository;

    @Test
    void normalAgentViewHidesGrimoireAndOtherPrivateInfo() {
        TestGame game = createGame("EMPATH");
        eventAppender.append(game.game(), "PUBLIC_SPEECH", game.agentSeat().getId(), null,
                "PUBLIC", List.of(), Map.of("content", "我是共情者"), Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.agentSeat().getId(), game.agentSeat().getId(),
                "PRIVATE", List.of(game.agentSeat().getId()), Map.of("infoType", "EMPATH", "evilCount", 1),
                Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
                "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
                Instant.now());

        AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

        assertThat(view.grimoire()).isEmpty();
        assertThat(view.myRoleCode()).isEqualTo("EMPATH");
        assertThat(view.publicSeats()).hasSize(2);
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.visibleEvents()).extracting("eventType")
                .contains("PUBLIC_SPEECH", "PRIVATE_INFO_RECEIVED");
        assertThat(view.privateInfos()).hasSize(1);
        assertThat(view.privateInfos().getFirst().payload()).containsEntry("infoType", "EMPATH");
    }

    @Test
    void spyAgentViewIncludesGrimoireButStillHidesOtherPrivateInfoMessages() {
        TestGame game = createGame("SPY");
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
                "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
                Instant.now());

        AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

        assertThat(view.grimoire()).hasSize(2);
        assertThat(view.grimoire()).extracting("roleCode").contains("SPY", "CHEF");
        assertThat(view.visibleEvents()).isEmpty();
        assertThat(view.privateInfos()).isEmpty();
    }

    @Test
    void legalIntentsExposeSpeechOnlyForCurrentMicHolder() {
        TestGame game = createGoodGame("EMPATH");
        ClocktowerGamePublicMicSessionPo session = new ClocktowerGamePublicMicSessionPo();
        session.setGameId(game.game().getId());
        session.setDayNo(game.game().getDayNo());
        session.setStatus("ROUND_ROBIN");
        session.setCurrentHolderGameSeatId(game.otherSeat().getId());
        session.setMetadataJson("{}");
        micSessionRepository.saveAndFlush(session);

        AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

        assertThat(view.legalIntents()).extracting("intentType")
                .doesNotContain("PUBLIC_SPEECH", "PASS");
    }

    @Test
    void evilAgentViewIncludesEvilTeamButGoodAgentDoesNot() {
        TestGame evilGame = createGame("SPY");
        addDemonSeat(evilGame.game());

        AgentPrivateView evilView = privateViewService.build(evilGame.game().getId(), evilGame.instance().getId());

        assertThat(evilView.roleSpecificContext()).containsKey("evilTeam");
        assertThat(evilView.roleSpecificContext()).containsKey("demonGameSeatId");

        TestGame goodGame = createGoodGame("EMPATH");
        AgentPrivateView goodView = privateViewService.build(goodGame.game().getId(), goodGame.instance().getId());

        assertThat(goodView.roleSpecificContext()).doesNotContainKeys("evilTeam", "demonGameSeatId");
    }

    @Test
    void legalIntentsExposeNominationTargetsWhenPhaseAllows() {
        TestGame game = createGoodGame("MONK");
        ClocktowerGamePo po = game.game();
        po.setPhase("NOMINATION");
        gameRepository.saveAndFlush(po);

        AgentPrivateView view = privateViewService.build(po.getId(), game.instance().getId());

        assertThat(view.legalIntents()).anySatisfy(intent -> {
            assertThat(intent.intentType()).isEqualTo("NOMINATE");
            assertThat(intent.payload()).containsEntry("eligibleTargetGameSeatIds", List.of(game.otherSeat().getId()));
        });
    }

    @Test
    void legalIntentsExposeNightChoiceTargetsFromRoleSkill() {
        TestGame game = createGoodGame("MONK");
        ClocktowerGamePo po = game.game();
        po.setPhase("NIGHT");
        po.setNightNo(2);
        gameRepository.saveAndFlush(po);
        ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
        task.setGameId(po.getId());
        task.setNightNo(po.getNightNo());
        task.setTaskKey("MONK:%s".formatted(game.agentSeat().getId()));
        task.setActorGameSeatId(game.agentSeat().getId());
        task.setRoleCode("MONK");
        task.setTaskType("TARGET");
        task.setStatus("PENDING");
        task.setSortOrder(1);
        task.setChoiceJson("{}");
        task.setResultJson("{}");
        task.setMetadataJson("{}");
        nightTaskRepository.saveAndFlush(task);

        AgentPrivateView view = privateViewService.build(po.getId(), game.instance().getId());

        assertThat(view.legalIntents()).anySatisfy(intent -> {
            assertThat(intent.intentType()).isEqualTo("NIGHT_CHOICE");
            assertThat(intent.taskId()).isEqualTo(task.getId());
            assertThat(intent.payload()).containsEntry("legalTargetGameSeatIds", List.of(game.otherSeat().getId()));
        });
    }

    private TestGame createGame(String agentRoleCode) {
        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(99001L + System.nanoTime());
        game.setGameNo(1);
        game.setScriptCode("TROUBLE_BREWING");
        game.setStatus("RUNNING");
        game.setPhase("DAY");
        game.setDayNo(1);
        game.setBoardSnapshotJson("{}");
        game.setMetadataJson("{}");
        game = gameRepository.saveAndFlush(game);

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType("AGENT");
        actor.setDisplayName("Agent");
        actor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(game.getRoomId());
        instance.setGameId(game.getId());
        instance.setProfileId(profileRepository.findFirstByNameAndDeletedFalse("balanced").orElseThrow().getId());
        instance.setActorId(actor.getId());
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setMetadataJson("{}");
        instance = agentInstanceRepository.saveAndFlush(instance);

        ClocktowerGameSeatPo agentSeat = new ClocktowerGameSeatPo();
        agentSeat.setGameId(game.getId());
        agentSeat.setSeatNo(1);
        agentSeat.setActorId(actor.getId());
        agentSeat.setActorType("AGENT");
        agentSeat.setAgentInstanceId(instance.getId());
        agentSeat.setDisplayName("Agent 1");
        agentSeat.setRoleCode(agentRoleCode);
        agentSeat.setRoleType("MINION");
        agentSeat.setAlignment("EVIL");
        agentSeat.setStatus("ACTIVE");
        agentSeat = gameSeatRepository.saveAndFlush(agentSeat);

        instance.setGameSeatId(agentSeat.getId());
        agentInstanceRepository.saveAndFlush(instance);

        ClocktowerGameSeatPo otherSeat = new ClocktowerGameSeatPo();
        otherSeat.setGameId(game.getId());
        otherSeat.setSeatNo(2);
        otherSeat.setActorType("HUMAN");
        otherSeat.setUserId(88002L);
        otherSeat.setDisplayName("Player 2");
        otherSeat.setRoleCode("CHEF");
        otherSeat.setRoleType("TOWNSFOLK");
        otherSeat.setAlignment("GOOD");
        otherSeat.setStatus("ACTIVE");
        otherSeat = gameSeatRepository.saveAndFlush(otherSeat);

        return new TestGame(game, instance, agentSeat, otherSeat);
    }

    private TestGame createGoodGame(String agentRoleCode) {
        TestGame game = createGame(agentRoleCode);
        game.agentSeat().setAlignment("GOOD");
        game.agentSeat().setRoleType("TOWNSFOLK");
        gameSeatRepository.saveAndFlush(game.agentSeat());
        return game;
    }

    private ClocktowerGameSeatPo addDemonSeat(ClocktowerGamePo game) {
        ClocktowerGameSeatPo demonSeat = new ClocktowerGameSeatPo();
        demonSeat.setGameId(game.getId());
        demonSeat.setSeatNo(3);
        demonSeat.setActorType("AGENT");
        demonSeat.setDisplayName("Demon");
        demonSeat.setRoleCode("IMP");
        demonSeat.setRoleType("DEMON");
        demonSeat.setAlignment("EVIL");
        demonSeat.setStatus("ACTIVE");
        return gameSeatRepository.saveAndFlush(demonSeat);
    }

    private record TestGame(ClocktowerGamePo game, ClocktowerAgentInstancePo instance,
                            ClocktowerGameSeatPo agentSeat, ClocktowerGameSeatPo otherSeat) {
    }
}
