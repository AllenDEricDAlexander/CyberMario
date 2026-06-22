package top.egon.mario.agent.memory.checkpoint;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Spring AI Alibaba PostgreSQL checkpoint saver.
 */
@ConfigurationProperties(prefix = "mario.agent.memory.checkpointer")
public class PostgresSaverProperties {

    private boolean enabled;

    private boolean createTables;

    private boolean dropTablesFirst;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCreateTables() {
        return createTables;
    }

    public void setCreateTables(boolean createTables) {
        this.createTables = createTables;
    }

    public boolean isDropTablesFirst() {
        return dropTablesFirst;
    }

    public void setDropTablesFirst(boolean dropTablesFirst) {
        this.dropTablesFirst = dropTablesFirst;
    }
}
