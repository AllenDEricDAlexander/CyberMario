package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

@Configuration
public class TroubleBrewingRoleSkillConfiguration {

    @Bean
    public RoleSkill washerwomanRoleSkill() {
        return new FirstNightInfoRoleSkill("WASHERWOMAN");
    }

    @Bean
    public RoleSkill librarianRoleSkill() {
        return new FirstNightInfoRoleSkill("LIBRARIAN");
    }

    @Bean
    public RoleSkill investigatorRoleSkill() {
        return new FirstNightInfoRoleSkill("INVESTIGATOR");
    }
}
