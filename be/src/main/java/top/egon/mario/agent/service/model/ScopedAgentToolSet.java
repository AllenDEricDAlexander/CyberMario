package top.egon.mario.agent.service.model;

import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable callbacks available only to one explicitly created agent runtime.
 */
public final class ScopedAgentToolSet {

    private static final ScopedAgentToolSet EMPTY = new ScopedAgentToolSet(List.of());

    private final List<ToolCallback> callbacks;

    private ScopedAgentToolSet(Collection<ScopedTool> tools) {
        Collection<ScopedTool> safeTools = tools == null ? List.of() : tools;
        Set<String> names = new LinkedHashSet<>();
        this.callbacks = safeTools.stream()
                .map(tool -> validate(tool, names))
                .toList();
    }

    /**
     * Returns an empty scoped tool set for callers that do not need per-run tools.
     */
    public static ScopedAgentToolSet empty() {
        return EMPTY;
    }

    /**
     * Creates a scoped set whose callbacks are explicitly classified as read-only.
     */
    public static ScopedAgentToolSet readOnly(Collection<ToolCallback> callbacks) {
        Collection<ToolCallback> safeCallbacks = callbacks == null ? List.of() : callbacks;
        return new ScopedAgentToolSet(safeCallbacks.stream().map(ScopedTool::readOnly).toList());
    }

    /**
     * Creates a scoped set whose callbacks are explicitly classified as read-only.
     */
    public static ScopedAgentToolSet readOnly(ToolCallback... callbacks) {
        return readOnly(callbacks == null ? List.of() : Arrays.asList(callbacks));
    }

    /**
     * Creates a scoped set from callbacks carrying an explicit access classification.
     */
    public static ScopedAgentToolSet of(Collection<ScopedTool> tools) {
        return new ScopedAgentToolSet(tools);
    }

    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    public boolean isEmpty() {
        return callbacks.isEmpty();
    }

    private static ToolCallback validate(ScopedTool tool, Set<String> names) {
        ScopedTool requiredTool = Objects.requireNonNull(tool, "scoped tool must not be null");
        if (requiredTool.accessMode() != AccessMode.READ_ONLY) {
            throw new IllegalArgumentException("scoped agent tools must be read-only");
        }
        ToolCallback callback = Objects.requireNonNull(requiredTool.callback(), "scoped callback must not be null");
        if (callback.getToolDefinition() == null || callback.getToolDefinition().name() == null
                || callback.getToolDefinition().name().isBlank()) {
            throw new IllegalArgumentException("scoped callback must have a name");
        }
        String name = callback.getToolDefinition().name();
        if (!names.add(name)) {
            throw new IllegalArgumentException("duplicate scoped callback name: " + name);
        }
        return callback;
    }

    public enum AccessMode {
        READ_ONLY,
        SIDE_EFFECTING
    }

    /**
     * Callback plus its declared effect boundary.
     */
    public record ScopedTool(ToolCallback callback, AccessMode accessMode) {

        public ScopedTool {
            Objects.requireNonNull(callback, "scoped callback must not be null");
            Objects.requireNonNull(accessMode, "scoped tool access mode must not be null");
        }

        public static ScopedTool readOnly(ToolCallback callback) {
            return new ScopedTool(callback, AccessMode.READ_ONLY);
        }

        public static ScopedTool sideEffecting(ToolCallback callback) {
            return new ScopedTool(callback, AccessMode.SIDE_EFFECTING);
        }
    }
}
