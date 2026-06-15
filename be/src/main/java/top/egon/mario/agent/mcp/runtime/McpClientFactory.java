package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.service.AgentException;

import java.time.Duration;
import java.util.Map;

/**
 * Creates initialized MCP clients from persisted server configuration.
 */
@Component
@RequiredArgsConstructor
public class McpClientFactory {

    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public McpSyncClient create(McpServerConfigPo server) {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(server.getBaseUrl());
        readHeaders(server.getHeadersJson()).forEach((name, value) ->
                webClientBuilder.defaultHeader(name, value));

        McpClientTransport transport = switch (server.getTransportType()) {
            case STREAMABLE_HTTP -> WebClientStreamableHttpTransport.builder(webClientBuilder)
                    .endpoint(server.getEndpoint())
                    .build();
            case SSE -> WebFluxSseClientTransport.builder(webClientBuilder)
                    .sseEndpoint(server.getEndpoint())
                    .build();
        };

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("CyberMario", "0.0.1"))
                .requestTimeout(Duration.ofMillis(server.getRequestTimeoutMs()))
                .initializationTimeout(Duration.ofMillis(server.getConnectTimeoutMs()))
                .build();
        try {
            client.initialize();
            return client;
        } catch (RuntimeException e) {
            closeClient(client, e);
            throw e;
        }
    }

    private Map<String, String> readHeaders(String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(headersJson, HEADERS_TYPE);
        } catch (JsonProcessingException e) {
            throw new AgentException("AGENT_MCP_HEADERS_INVALID", "mcp server headers are invalid");
        }
    }

    private void closeClient(McpSyncClient client, RuntimeException original) {
        try {
            if (!client.closeGracefully()) {
                client.close();
            }
        } catch (RuntimeException closeGracefullyException) {
            original.addSuppressed(closeGracefullyException);
            try {
                client.close();
            } catch (RuntimeException closeException) {
                original.addSuppressed(closeException);
            }
        }
    }

}
