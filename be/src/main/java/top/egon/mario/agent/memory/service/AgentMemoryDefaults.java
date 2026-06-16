package top.egon.mario.agent.memory.service;

/**
 * Shared defaults for Agent memory persistence and prompt assembly.
 */
public final class AgentMemoryDefaults {

    public static final int SHORT_TERM_WINDOW_TURNS = 10;

    public static final int LONG_TERM_MARKDOWN_MAX_CHARS = 20_000;

    public static final String DEFAULT_USER_MEMORY_MARKDOWN = """
            # User Memory

            ## Preferences

            ## Stable Facts

            ## Working Style

            ## Project And Tooling Notes

            ## RAG-Derived Notes

            ## Do Not Forget

            ## Source Index
            """;

    private AgentMemoryDefaults() {
    }
}
