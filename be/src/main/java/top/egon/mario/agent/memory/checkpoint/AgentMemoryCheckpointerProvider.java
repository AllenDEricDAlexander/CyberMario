package top.egon.mario.agent.memory.checkpoint;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provides the ReactAgent checkpoint saver used by memory-enabled chats.
 */
@Component
@EnableConfigurationProperties(PostgresSaverProperties.class)
public class AgentMemoryCheckpointerProvider {

    private final BaseCheckpointSaver saver;

    public AgentMemoryCheckpointerProvider(DataSourceProperties dataSourceProperties,
                                           PostgresSaverProperties properties) {
        this.saver = buildSaver(dataSourceProperties, properties);
    }

    public BaseCheckpointSaver saver() {
        return saver;
    }

    private BaseCheckpointSaver buildSaver(DataSourceProperties dataSourceProperties,
                                           PostgresSaverProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            return new MemorySaver();
        }
        JdbcUrlParts parts = JdbcUrlParts.parse(dataSourceProperties.getUrl());
        return PostgresSaver.builder()
                .host(parts.host())
                .port(parts.port())
                .database(parts.database())
                .user(dataSourceProperties.getUsername())
                .password(dataSourceProperties.getPassword())
                .createTables(properties.isCreateTables())
                .dropTablesFirst(properties.isDropTablesFirst())
                .build();
    }

    record JdbcUrlParts(String host, int port, String database) {

        static JdbcUrlParts parse(String url) {
            if (!StringUtils.hasText(url) || !url.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("PostgresSaver requires a jdbc:postgresql datasource url");
            }
            String rest = url.substring("jdbc:postgresql://".length());
            int slashIndex = rest.indexOf('/');
            if (slashIndex < 0 || slashIndex == rest.length() - 1) {
                throw new IllegalArgumentException("PostgresSaver requires a database name in datasource url");
            }
            String hostPort = rest.substring(0, slashIndex);
            String database = rest.substring(slashIndex + 1);
            int queryIndex = database.indexOf('?');
            if (queryIndex >= 0) {
                database = database.substring(0, queryIndex);
            }
            String host = hostPort;
            int port = 5432;
            int colonIndex = hostPort.lastIndexOf(':');
            if (colonIndex > 0) {
                host = hostPort.substring(0, colonIndex);
                port = Integer.parseInt(hostPort.substring(colonIndex + 1));
            }
            return new JdbcUrlParts(host, port, database);
        }
    }
}
