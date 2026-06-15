package top.egon.mario.agent.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.mcp.dto.response.McpToolCallLogResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolCallLogPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;
import top.egon.mario.agent.mcp.repository.McpToolCallLogRepository;
import top.egon.mario.agent.service.AgentException;

import java.time.Instant;
import java.util.Map;

/**
 * Persists MCP tool execution audit logs.
 */
@Service
@RequiredArgsConstructor
public class McpToolCallLogService {

    private static final int SUMMARY_LIMIT = 4000;
    private static final int ERROR_LIMIT = 1024;

    private final McpToolCallLogRepository repository;

    @Transactional
    public void recordSuccess(McpServerConfigPo server, McpToolConfigPo tool, String args, String response,
                              long costMs, ToolContext toolContext) {
        McpToolCallLogPo log = newLog(server, tool, args, costMs, toolContext);
        log.setStatus(McpToolCallStatus.SUCCESS);
        log.setResponseSummary(limit(response, SUMMARY_LIMIT));
        repository.save(log);
    }

    @Transactional
    public void recordFailure(McpServerConfigPo server, McpToolConfigPo tool, String args, Throwable error,
                              long costMs, ToolContext toolContext) {
        McpToolCallLogPo log = newLog(server, tool, args, costMs, toolContext);
        log.setStatus(McpToolCallStatus.FAILED);
        log.setErrorMsg(limit(error.getMessage(), ERROR_LIMIT));
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<McpToolCallLogResponse> page(Pageable pageable) {
        return repository.findAllByOrderByIdDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public McpToolCallLogResponse get(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AgentException("AGENT_MCP_TOOL_CALL_LOG_NOT_FOUND",
                        "mcp tool call log not found"));
    }

    private McpToolCallLogPo newLog(McpServerConfigPo server, McpToolConfigPo tool, String args, long costMs,
                                    ToolContext toolContext) {
        McpToolCallLogPo log = new McpToolCallLogPo();
        log.setServerCode(server.getServerCode());
        log.setToolKey(tool.getToolKey());
        log.setToolName(tool.getToolName());
        log.setRequestArgsSummary(limit(args, SUMMARY_LIMIT));
        log.setCostMs(costMs);
        log.setCreatedAt(Instant.now());
        applyContext(log, toolContext);
        return log;
    }

    private void applyContext(McpToolCallLogPo log, ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return;
        }
        Map<String, Object> context = toolContext.getContext();
        log.setTraceId(asString(context.get("traceId")));
        log.setThreadId(asString(context.get("threadId")));
        log.setUserId(asLong(context.get("userId")));
    }

    private McpToolCallLogResponse toResponse(McpToolCallLogPo log) {
        return new McpToolCallLogResponse(
                log.getId(),
                log.getTraceId(),
                log.getThreadId(),
                log.getUserId(),
                log.getServerCode(),
                log.getToolKey(),
                log.getToolName(),
                log.getRequestArgsSummary(),
                log.getResponseSummary(),
                log.getStatus(),
                log.getErrorMsg(),
                log.getCostMs(),
                log.getCreatedAt());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

}
