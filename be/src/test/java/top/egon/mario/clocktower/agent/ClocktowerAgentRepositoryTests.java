package top.egon.mario.clocktower.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentRepositoryTests {

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentProfileRepository profileRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository instanceRepository;

    @Test
    void seededAgentProfilesAreReadableInIdOrder() {
        assertThat(profileRepository.findByDeletedFalseOrderByIdAsc())
                .extracting(ClocktowerAgentProfilePo::getName)
                .contains("balanced", "quiet", "aggressive", "careful");

        ClocktowerAgentProfilePo balanced = profileRepository.findFirstByNameAndDeletedFalse("balanced")
                .orElseThrow();
        assertThat(balanced.getDisplayNameTemplate()).isEqualTo("Agent {n}");
        assertThat(balanced.getStrategyLevel()).isEqualTo("NORMAL");
        assertThat(balanced.getTalkativeness()).isEqualTo(50);
        assertThat(balanced.getDeceptionLevel()).isEqualTo(50);
        assertThat(balanced.getAggression()).isEqualTo(50);
        assertThat(balanced.getRiskTolerance()).isEqualTo(50);
    }

    @Test
    void repositoriesPersistAgentActorAndInstanceRows() {
        ClocktowerAgentProfilePo balanced = profileRepository.findFirstByNameAndDeletedFalse("balanced")
                .orElseThrow();

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType(ClocktowerActorType.AGENT);
        actor.setDisplayName("Agent 7");
        actor.setStatus(ClocktowerAgentStatus.ACTIVE);
        actor.setMetadataJson("{\"source\":\"repository-test\"}");
        ClocktowerActorPo savedActor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(72001L);
        instance.setProfileId(balanced.getId());
        instance.setActorId(savedActor.getId());
        instance.setRoomSeatId(73001L);
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setAutoMode(ClocktowerAgentAutoMode.FULL_AUTO);
        instance.setMetadataJson("{\"source\":\"repository-test\"}");
        ClocktowerAgentInstancePo savedInstance = instanceRepository.saveAndFlush(instance);

        assertThat(actorRepository.findByIdAndDeletedFalse(savedActor.getId()))
                .get()
                .extracting(ClocktowerActorPo::getDisplayName)
                .isEqualTo("Agent 7");
        assertThat(instanceRepository.findByIdAndDeletedFalse(savedInstance.getId())).isPresent();
        assertThat(instanceRepository.findByActorIdAndDeletedFalse(savedActor.getId()))
                .get()
                .extracting(ClocktowerAgentInstancePo::getRoomId)
                .isEqualTo(72001L);
        assertThat(instanceRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(72001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .contains(savedInstance.getId());
        assertThat(instanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(99999L)).isEmpty();
    }
}
