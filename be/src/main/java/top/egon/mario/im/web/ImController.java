package top.egon.mario.im.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/im")
public class ImController {

    private final ImFacade imFacade;
    private final Scheduler blockingScheduler;

    @PostMapping("/ws-ticket")
    public Mono<ApiResponse<WsTicketView>> mintWsTicket(@AuthenticationPrincipal RbacPrincipal principal,
                                                        @RequestBody(required = false) MintWsTicketRequest request) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId,
                            () -> imFacade.mintWsTicket(new MintWsTicketCommand(
                                    imPrincipal(principal),
                                    request == null ? null : request.conversationId()
                            ))))
                    .map(ticket -> ApiResponse.ok(ticket, traceId))
                    .subscribeOn(blockingScheduler);
        });
    }

    private ImPrincipal imPrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_WS_TICKET_PRINCIPAL_REQUIRED");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        if (principal.username() != null) {
            attributes.put("username", principal.username());
        }
        if (principal.permissionVersion() != null) {
            attributes.put("permissionVersion", principal.permissionVersion());
        }
        return new ImPrincipal(principal.userId(), principal.roleCodes(), "RBAC", attributes);
    }

    public record MintWsTicketRequest(Long conversationId) {
    }
}
