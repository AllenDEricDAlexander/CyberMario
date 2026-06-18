package top.egon.mario.clocktower.ruling.web;

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
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}/rulings")
@Validated
public class ClocktowerRulingController extends ClocktowerReactiveSupport {

    private final ClocktowerRulingService rulingService;

    @PostMapping
    public Mono<ApiResponse<ClocktowerRulingApplyResponse>> create(@PathVariable Long roomId,
                                                                   @RequestBody ClocktowerRulingCreateRequest request,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.create(roomId, request, principal));
    }

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerRulingResponse>>> list(@PathVariable Long roomId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.list(roomId, principal));
    }

    @PostMapping("/{rulingId}/undo")
    public Mono<ApiResponse<ClocktowerRulingApplyResponse>> undo(@PathVariable Long roomId,
                                                                 @PathVariable Long rulingId,
                                                                 @RequestBody ClocktowerRulingUndoRequest request,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.undo(roomId, rulingId, request, principal));
    }
}
