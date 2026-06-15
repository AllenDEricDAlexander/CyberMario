package top.egon.mario.agent.observability.service.model;

import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public record AgentRunAuditContext(
        Long runId,
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        AtomicInteger sequence,
        AtomicInteger reactRound,
        Map<String, ToolDescriptor> toolDescriptors,
        AtomicBoolean finished
) {

    public static final String METADATA_KEY = "agentRunAuditContext";

    public AgentRunAuditContext(Long runId, String requestId, String traceId, Long userId, String username,
                                String threadId, Long presetId, String runtimeFingerprint, AtomicInteger sequence,
                                AtomicInteger reactRound, Map<String, ToolDescriptor> toolDescriptors) {
        this(runId, requestId, traceId, userId, username, threadId, presetId, runtimeFingerprint, sequence,
                reactRound, toolDescriptors, new AtomicBoolean(false));
    }

    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY, this);
        putIfNotNull(metadata, "requestId", requestId);
        putIfNotNull(metadata, "traceId", traceId);
        putIfNotNull(metadata, "threadId", threadId);
        putIfNotNull(metadata, "userId", userId);
        return metadata;
    }

    public int nextSeq() {
        return sequence.incrementAndGet();
    }

    public int nextReactRound() {
        return reactRound.incrementAndGet();
    }

    public ToolDescriptor toolDescriptor(String toolName) {
        if (toolName == null || toolDescriptors == null) {
            return null;
        }
        return toolDescriptors.get(toolName);
    }

    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    public record ToolDescriptor(AgentRunToolType toolType, String mcpServerCode) {
    }
}
