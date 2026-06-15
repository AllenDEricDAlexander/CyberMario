package top.egon.mario.agent.mcp.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.mcp.dto.response.McpToolCallLogResponse;
import top.egon.mario.agent.mcp.service.McpToolCallLogService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;

/**
 * Admin endpoints for MCP tool call audit logs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/mcp/tool-calls")
@Validated
public class AdminMcpToolCallLogController extends McpReactiveSupport {

    private final McpToolCallLogService logService;

    @GetMapping
    public Mono<ApiResponse<PageResult<McpToolCallLogResponse>>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return blocking(() -> pageResult(logService.page(PageRequest.of(Math.max(page - 1, 0), size,
                Sort.by("id").descending()))));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<McpToolCallLogResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> logService.get(id));
    }

}
