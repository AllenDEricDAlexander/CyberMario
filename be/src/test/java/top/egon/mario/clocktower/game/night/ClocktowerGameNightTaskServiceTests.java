package top.egon.mario.clocktower.game.night;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameNightTaskServiceTests {

    @Autowired(required = false)
    private ClocktowerGameNightTaskService taskService;

    @Autowired(required = false)
    private ClocktowerNightResolutionService resolutionService;

    @Autowired(required = false)
    private ClocktowerRoleSkillRegistry roleSkillRegistry;

    @Test
    void nightServicesAreAvailable() {
        assertThat(taskService).isNotNull();
        assertThat(resolutionService).isNotNull();
        assertThat(roleSkillRegistry).isNotNull();
        assertThat(roleSkillRegistry.find("POISONER")).isPresent();
        assertThat(roleSkillRegistry.find("IMP")).isPresent();
    }
}
