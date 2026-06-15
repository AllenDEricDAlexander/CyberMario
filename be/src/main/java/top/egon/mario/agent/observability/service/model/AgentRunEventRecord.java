package top.egon.mario.agent.observability.service.model;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

public record AgentRunEventRecord(
        AgentRunEventType eventType,
        Integer reactRound,
        String toolCallId,
        String toolName,
        AgentRunToolType toolType,
        String mcpServerCode,
        AgentRunEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ModelProviderType modelProvider,
        String modelName,
        String promptText,
        String requestMessagesJson,
        String requestOptionsJson,
        String availableToolsJson,
        String responseText,
        String toolArguments,
        String toolResult,
        String metadataJson,
        String errorCode,
        String errorMessage
) {

    public static Builder builder(AgentRunEventType eventType) {
        return new Builder(eventType);
    }

    public static final class Builder {
        private final AgentRunEventType eventType;
        private Integer reactRound;
        private String toolCallId;
        private String toolName;
        private AgentRunToolType toolType;
        private String mcpServerCode;
        private AgentRunEventStatus status = AgentRunEventStatus.SUCCESS;
        private Instant startedAt;
        private Instant finishedAt;
        private Long durationMs;
        private ModelProviderType modelProvider;
        private String modelName;
        private String promptText;
        private String requestMessagesJson;
        private String requestOptionsJson;
        private String availableToolsJson;
        private String responseText;
        private String toolArguments;
        private String toolResult;
        private String metadataJson;
        private String errorCode;
        private String errorMessage;

        private Builder(AgentRunEventType eventType) {
            this.eventType = eventType;
        }

        public Builder reactRound(Integer reactRound) {
            this.reactRound = reactRound;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolType(AgentRunToolType toolType) {
            this.toolType = toolType;
            return this;
        }

        public Builder mcpServerCode(String mcpServerCode) {
            this.mcpServerCode = mcpServerCode;
            return this;
        }

        public Builder status(AgentRunEventStatus status) {
            this.status = status;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder modelProvider(ModelProviderType modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder promptText(String promptText) {
            this.promptText = promptText;
            return this;
        }

        public Builder requestMessagesJson(String requestMessagesJson) {
            this.requestMessagesJson = requestMessagesJson;
            return this;
        }

        public Builder requestOptionsJson(String requestOptionsJson) {
            this.requestOptionsJson = requestOptionsJson;
            return this;
        }

        public Builder availableToolsJson(String availableToolsJson) {
            this.availableToolsJson = availableToolsJson;
            return this;
        }

        public Builder responseText(String responseText) {
            this.responseText = responseText;
            return this;
        }

        public Builder toolArguments(String toolArguments) {
            this.toolArguments = toolArguments;
            return this;
        }

        public Builder toolResult(String toolResult) {
            this.toolResult = toolResult;
            return this;
        }

        public Builder metadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AgentRunEventRecord build() {
            return new AgentRunEventRecord(eventType, reactRound, toolCallId, toolName, toolType, mcpServerCode,
                    status, startedAt, finishedAt, durationMs, modelProvider, modelName, promptText,
                    requestMessagesJson, requestOptionsJson, availableToolsJson, responseText, toolArguments,
                    toolResult, metadataJson, errorCode, errorMessage);
        }
    }
}
