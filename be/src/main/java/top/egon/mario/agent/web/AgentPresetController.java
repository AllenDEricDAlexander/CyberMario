package top.egon.mario.agent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.dto.request.AgentPresetRequest;
import top.egon.mario.agent.dto.request.AgentPresetStatusRequest;
import top.egon.mario.agent.dto.response.AgentPresetResponse;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Management endpoints for Agent debug presets.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/presets")
@Validated
public class AgentPresetController extends ReactiveAgentSupport {

    private final AgentPresetService presetService;

    @GetMapping
    public Mono<ApiResponse<PageResult<AgentPresetResponse>>> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                   @RequestParam(defaultValue = "20") @Min(1) int size) {
        return blocking(() -> pageResult(presetService.page(PageRequest.of(Math.max(page - 1, 0), size,
                Sort.by("id").descending()))));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentPresetResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> presetService.detail(id));
    }

    @PostMapping
    public Mono<ApiResponse<AgentPresetResponse>> create(@Valid @RequestBody AgentPresetRequest request,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> presetService.create(request, principal));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<AgentPresetResponse>> update(@PathVariable @Min(1) Long id,
                                                         @Valid @RequestBody AgentPresetRequest request,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> presetService.update(id, request, principal));
    }

    @PatchMapping("/{id}/status")
    public Mono<ApiResponse<AgentPresetResponse>> updateStatus(@PathVariable @Min(1) Long id,
                                                               @Valid @RequestBody AgentPresetStatusRequest request,
                                                               @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> presetService.updateStatus(id, request, principal));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable @Min(1) Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> presetService.delete(id, principal));
    }

}
