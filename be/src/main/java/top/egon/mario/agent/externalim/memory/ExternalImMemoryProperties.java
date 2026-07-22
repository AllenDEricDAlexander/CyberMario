package top.egon.mario.agent.externalim.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "mario.agent.external-im.memory")
public record ExternalImMemoryProperties(
        @DefaultValue("40") int timelineMaxEvents,
        @DefaultValue("12000") int timelineMaxChars,
        @DefaultValue("12") int guardWindowEvents,
        @DefaultValue("3000") int guardWindowMaxChars
) {

    @ConstructorBinding
    public ExternalImMemoryProperties {
        timelineMaxEvents = Math.max(1, Math.min(timelineMaxEvents, 80));
        timelineMaxChars = Math.max(1000, Math.min(timelineMaxChars, 20000));
        guardWindowEvents = Math.max(1, Math.min(guardWindowEvents, 12));
        guardWindowMaxChars = Math.max(500, Math.min(guardWindowMaxChars, 5000));
    }
}
