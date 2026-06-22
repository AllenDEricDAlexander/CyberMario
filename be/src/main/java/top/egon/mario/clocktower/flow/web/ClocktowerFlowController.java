package top.egon.mario.clocktower.flow.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.clocktower.flow.service.ClocktowerFlowService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}")
@Validated
public class ClocktowerFlowController extends ClocktowerReactiveSupport {

    private final ClocktowerFlowService flowService;

    @GetMapping("/flow")
    public Mono<ApiResponse<ClocktowerFlowResponse>> getFlow(@PathVariable Long roomId,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.getFlow(roomId, principal));
    }

    @PostMapping("/flow/advance")
    public Mono<ApiResponse<ClocktowerFlowResponse>> advance(@PathVariable Long roomId,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.advance(roomId, principal));
    }

    @PostMapping("/night-tasks/{taskId}/skip")
    public Mono<ApiResponse<ClocktowerFlowResponse>> skipNightTask(@PathVariable Long roomId,
                                                                   @PathVariable Long taskId,
                                                                   @RequestBody SkipNightTaskRequest request,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.skipNightTask(roomId, taskId, request, principal));
    }

    @PostMapping("/nominations/{nominationId}/close")
    public Mono<ApiResponse<ClocktowerFlowResponse>> closeNomination(@PathVariable Long roomId,
                                                                     @PathVariable Long nominationId,
                                                                     @RequestBody CloseNominationRequest request,
                                                                     @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.closeNomination(roomId, nominationId, request, principal));
    }

    @PostMapping("/execution/confirm")
    public Mono<ApiResponse<ClocktowerFlowResponse>> confirmExecution(@PathVariable Long roomId,
                                                                      @RequestBody ExecutionConfirmRequest request,
                                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.confirmExecution(roomId, request, principal));
    }
}
