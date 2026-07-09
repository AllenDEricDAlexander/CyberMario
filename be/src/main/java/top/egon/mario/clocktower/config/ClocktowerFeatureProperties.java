package top.egon.mario.clocktower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clocktower")
public record ClocktowerFeatureProperties(
        FeatureFlag agentPlayer,
        FeatureFlag gameActions,
        FeatureFlag newFlow,
        FeatureFlag llmAgent
) {

    public ClocktowerFeatureProperties {
        agentPlayer = agentPlayer == null ? FeatureFlag.enabledFlag() : agentPlayer;
        gameActions = gameActions == null ? FeatureFlag.enabledFlag() : gameActions;
        newFlow = newFlow == null ? FeatureFlag.enabledFlag() : newFlow;
        llmAgent = llmAgent == null ? FeatureFlag.disabledFlag() : llmAgent;
    }

    public record FeatureFlag(boolean enabled) {

        public static FeatureFlag enabledFlag() {
            return new FeatureFlag(true);
        }

        public static FeatureFlag disabledFlag() {
            return new FeatureFlag(false);
        }
    }
}
