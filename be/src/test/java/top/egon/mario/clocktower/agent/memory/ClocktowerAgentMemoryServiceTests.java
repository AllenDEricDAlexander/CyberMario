package top.egon.mario.clocktower.agent.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService.ClocktowerAgentMemoryRefreshResult;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
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
class ClocktowerAgentMemoryServiceTests {

    @Autowired
    private ClocktowerAgentMemoryService memoryService;

    @Autowired
    private ClocktowerAgentMemoryRepository memoryRepository;

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

    @Test
    void refreshRecordsPublicSpeechRoleClaimAndPrivateInfoOnce() {
        TestGame game = createGame("EMPATH");
        eventAppender.append(game.game(), "PUBLIC_SPEECH", game.otherSeat().getId(), null,
                "PUBLIC", List.of(), Map.of("content", "我是厨师，昨晚看到 0 对邪恶相邻。"), Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.agentSeat().getId(), game.agentSeat().getId(),
                "PRIVATE", List.of(game.agentSeat().getId()), Map.of("infoType", "EMPATH", "evilCount", 1),
                Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
                "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
                Instant.now());

        ClocktowerAgentMemoryRefreshResult first = memoryService.refresh(game.game().getId(), game.instance().getId());
        ClocktowerAgentMemoryRefreshResult second = memoryService.refresh(game.game().getId(), game.instance().getId());

        List<ClocktowerAgentMemoryPo> memories = memoryRepository
                .findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                        game.game().getId(), game.instance().getId());
        ClocktowerAgentInstancePo reloaded = agentInstanceRepository.findByIdAndDeletedFalse(game.instance().getId())
                .orElseThrow();

        assertThat(first.insertedCount()).isEqualTo(3);
        assertThat(second.insertedCount()).isZero();
        assertThat(memories).extracting(ClocktowerAgentMemoryPo::getMemoryType)
                .containsExactlyInAnyOrder("PUBLIC_SPEECH_SUMMARY", "ROLE_CLAIM", "PRIVATE_INFO");
        assertThat(memories).noneSatisfy(memory -> assertThat(memory.getContentJson()).contains("evilPairs"));
        assertThat(reloaded.getMetadataJson()).contains("\"lastSeenEventSeq\":3");
    }

    @Test
    void refreshRecordsNominationVoteAndDeathObservations() {
        TestGame game = createGame("EMPATH");
        eventAppender.append(game.game(), "NOMINATION_OPENED", game.agentSeat().getId(), game.otherSeat().getId(),
                "PUBLIC", List.of(), Map.of("nominationId", 9901L, "nominatorGameSeatId",
                        game.agentSeat().getId(), "nomineeGameSeatId", game.otherSeat().getId()), Instant.now());
        eventAppender.append(game.game(), "VOTE_CAST", game.otherSeat().getId(), game.agentSeat().getId(),
                "PUBLIC", List.of(), Map.of("nominationId", 9901L, "voterGameSeatId",
                        game.otherSeat().getId(), "voteValue", true), Instant.now());
        eventAppender.append(game.game(), "PLAYER_DIED", null, game.otherSeat().getId(),
                "PUBLIC", List.of(), Map.of("targetGameSeatId", game.otherSeat().getId()), Instant.now());

        memoryService.refresh(game.game().getId(), game.instance().getId());

        assertThat(memoryRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                        game.game().getId(), game.instance().getId()))
                .extracting(ClocktowerAgentMemoryPo::getMemoryType)
                .containsExactlyInAnyOrder("NOMINATION_OBSERVATION", "VOTE_OBSERVATION", "DEATH_OBSERVATION");
    }

    private TestGame createGame(String agentRoleCode) {
        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(99101L + System.nanoTime());
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
        agentSeat.setRoleType("TOWNSFOLK");
        agentSeat.setAlignment("GOOD");
        agentSeat.setStatus("ACTIVE");
        agentSeat = gameSeatRepository.saveAndFlush(agentSeat);

        instance.setGameSeatId(agentSeat.getId());
        agentInstanceRepository.saveAndFlush(instance);

        ClocktowerGameSeatPo otherSeat = new ClocktowerGameSeatPo();
        otherSeat.setGameId(game.getId());
        otherSeat.setSeatNo(2);
        otherSeat.setActorType("HUMAN");
        otherSeat.setUserId(88102L);
        otherSeat.setDisplayName("Player 2");
        otherSeat.setRoleCode("CHEF");
        otherSeat.setRoleType("TOWNSFOLK");
        otherSeat.setAlignment("GOOD");
        otherSeat.setStatus("ACTIVE");
        otherSeat = gameSeatRepository.saveAndFlush(otherSeat);

        return new TestGame(game, instance, agentSeat, otherSeat);
    }

    private record TestGame(ClocktowerGamePo game, ClocktowerAgentInstancePo instance,
                            ClocktowerGameSeatPo agentSeat, ClocktowerGameSeatPo otherSeat) {
    }
}
