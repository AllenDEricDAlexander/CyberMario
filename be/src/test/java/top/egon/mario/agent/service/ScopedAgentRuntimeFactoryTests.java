package top.egon.mario.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the immutable read-only boundary for callbacks passed to one runtime creation.
 */
class ScopedAgentRuntimeFactoryTests {

    @Test
    void readOnlyToolSetIsImmutableAndIsNotASpringComponent() {
        ToolCallback callback = tool("get_investment_portfolio", "private portfolio");
        ScopedAgentToolSet toolSet = ScopedAgentToolSet.readOnly(callback);

        assertThat(toolSet.callbacks()).containsExactly(callback);
        assertThatThrownBy(() -> toolSet.callbacks().add(tool("other", "other result")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(ScopedAgentToolSet.class.getAnnotations()).isEmpty();
    }

    @Test
    void duplicateNamesAndSideEffectingCallbacksAreRejected() {
        assertThatThrownBy(() -> ScopedAgentToolSet.readOnly(
                tool("get_investment_candles", "first"), tool("get_investment_candles", "second")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate scoped callback name");

        assertThatThrownBy(() -> ScopedAgentToolSet.of(List.of(
                ScopedAgentToolSet.ScopedTool.sideEffecting(
                        tool("submit_paper_trade_intent", "submitted")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be read-only");
    }

    @Test
    void blankCallbackNameIsRejected() {
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return new ToolDefinition() {
                    @Override
                    public String name() {
                        return "   ";
                    }

                    @Override
                    public String description() {
                        return "invalid test tool";
                    }

                    @Override
                    public String inputSchema() {
                        return "{}";
                    }
                };
            }

            @Override
            public String call(String toolInput) {
                return "ignored";
            }
        };

        assertThatThrownBy(() -> ScopedAgentToolSet.readOnly(callback))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a name");
    }

    @Test
    void callbackExceptionsPropagateWithoutScopedWrapper() {
        IllegalStateException failure = new IllegalStateException("portfolio unavailable");
        ToolCallback callback = throwingTool("get_investment_portfolio", failure);
        ScopedAgentToolSet toolSet = ScopedAgentToolSet.readOnly(callback);

        assertThatThrownBy(() -> toolSet.callbacks().get(0).call("{}"))
                .isSameAs(failure);
    }

    private ToolCallback tool(String name, String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition(name);
            }

            @Override
            public String call(String toolInput) {
                return result;
            }
        };
    }

    private ToolCallback throwingTool(String name, RuntimeException failure) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition(name);
            }

            @Override
            public String call(String toolInput) {
                throw failure;
            }
        };
    }

    private ToolDefinition definition(String name) {
        return ToolDefinition.builder().name(name).description("read-only test tool").inputSchema("{}").build();
    }
}
