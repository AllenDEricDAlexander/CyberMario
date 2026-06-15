package top.egon.mario.agent.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.agent.mcp.dto.request.CreateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.service.AgentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages persisted MCP server configuration records.
 */
@Service
@RequiredArgsConstructor
@Validated
public class McpServerConfigService {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {
    };

    private final McpServerConfigRepository serverRepository;
    private final McpToolConfigRepository toolRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<McpServerResponse> list() {
        return serverRepository.findByDeletedFalseOrderByIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public McpServerResponse get(Long id) {
        return toResponse(requireServer(id));
    }

    @Transactional
    public McpServerResponse create(CreateMcpServerRequest request, Long actorId) {
        String serverCode = normalizeServerCode(request.serverCode());
        if (serverRepository.existsByServerCodeAndDeletedFalse(serverCode)) {
            throw new AgentException("AGENT_MCP_SERVER_CODE_EXISTS", "mcp server code already exists");
        }
        McpServerConfigPo server = new McpServerConfigPo();
        server.setServerCode(serverCode);
        server.setServerName(request.serverName().trim());
        server.setTransportType(request.transportType());
        server.setBaseUrl(request.baseUrl().trim());
        server.setEndpoint(request.endpoint().trim());
        server.setHeadersJson(writeHeaders(request.headers()));
        server.setEnabled(false);
        server.setConnectTimeoutMs(timeoutOrDefault(request.connectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS));
        server.setRequestTimeoutMs(timeoutOrDefault(request.requestTimeoutMs(), DEFAULT_REQUEST_TIMEOUT_MS));
        server.setStatus(McpServerStatus.DISABLED);
        server.setCreatedBy(actorId);
        server.setUpdatedBy(actorId);
        return toResponse(serverRepository.save(server));
    }

    @Transactional
    public McpServerResponse update(Long id, UpdateMcpServerRequest request, Long actorId) {
        McpServerConfigPo server = requireServer(id);
        server.setServerName(request.serverName().trim());
        server.setTransportType(request.transportType());
        server.setBaseUrl(request.baseUrl().trim());
        server.setEndpoint(request.endpoint().trim());
        if (request.headers() != null) {
            server.setHeadersJson(writeHeaders(mergeMaskedHeaders(request.headers(), server.getHeadersJson())));
        }
        if (request.connectTimeoutMs() != null) {
            server.setConnectTimeoutMs(request.connectTimeoutMs());
        }
        if (request.requestTimeoutMs() != null) {
            server.setRequestTimeoutMs(request.requestTimeoutMs());
        }
        server.setUpdatedBy(actorId);
        return toResponse(serverRepository.save(server));
    }

    @Transactional
    public void enable(Long id, Long actorId) {
        McpServerConfigPo server = requireServer(id);
        server.setEnabled(true);
        if (server.getStatus() == McpServerStatus.DISABLED) {
            server.setStatus(McpServerStatus.CONNECTING);
        }
        server.setUpdatedBy(actorId);
        serverRepository.save(server);
    }

    @Transactional
    public void disable(Long id, Long actorId) {
        McpServerConfigPo server = requireServer(id);
        server.setEnabled(false);
        server.setStatus(McpServerStatus.DISABLED);
        server.setUpdatedBy(actorId);
        serverRepository.save(server);
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        McpServerConfigPo server = requireServer(id);
        server.setDeleted(true);
        server.setUpdatedBy(actorId);
        for (McpToolConfigPo tool : toolRepository.findByServerIdAndDeletedFalseOrderByIdAsc(id)) {
            tool.setDeleted(true);
            tool.setUpdatedBy(actorId);
            toolRepository.save(tool);
        }
        serverRepository.save(server);
    }

    @Transactional(readOnly = true)
    public McpServerConfigPo requireServer(Long id) {
        return serverRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AgentException("AGENT_MCP_SERVER_NOT_FOUND", "mcp server not found"));
    }

    private McpServerResponse toResponse(McpServerConfigPo server) {
        return new McpServerResponse(
                server.getId(),
                server.getServerCode(),
                server.getServerName(),
                server.getTransportType(),
                server.getBaseUrl(),
                server.getEndpoint(),
                maskHeaders(server.getHeadersJson()),
                server.isEnabled(),
                server.getConnectTimeoutMs(),
                server.getRequestTimeoutMs(),
                server.getStatus(),
                server.getLastError(),
                server.getLastConnectedAt(),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }

    private String normalizeServerCode(String serverCode) {
        return serverCode.trim().toLowerCase(Locale.ROOT);
    }

    private int timeoutOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String writeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            throw new AgentException("AGENT_MCP_HEADERS_INVALID", "mcp server headers cannot be serialized");
        }
    }

    private Map<String, String> mergeMaskedHeaders(Map<String, String> requestHeaders, String existingHeadersJson) {
        if (requestHeaders.isEmpty()) {
            return requestHeaders;
        }
        Map<String, String> existingHeaders = readHeaders(existingHeadersJson);
        if (existingHeaders.isEmpty()) {
            return requestHeaders;
        }
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        requestHeaders.forEach((name, value) -> {
            String existingValue = existingHeaders.get(name);
            if (existingValue != null && maskHeaderValue(existingValue).equals(value)) {
                mergedHeaders.put(name, existingValue);
            } else {
                mergedHeaders.put(name, value);
            }
        });
        return mergedHeaders;
    }

    private Map<String, String> maskHeaders(String headersJson) {
        Map<String, String> headers = readHeaders(headersJson);
        return headers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> maskHeaderValue(entry.getValue()),
                        (left, right) -> right, LinkedHashMap::new));
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

    private String maskHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.length() > 8) {
            return value.substring(0, 8) + "********";
        }
        return "********";
    }

}
