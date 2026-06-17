package top.egon.mario.clocktower.board.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/boards")
@Validated
public class ClocktowerBoardController {

    private final ClocktowerBoardService boardService;

    @PostMapping("/generate")
    public Mono<ClocktowerBoardGenerateResponse> generate(@Valid @RequestBody Mono<ClocktowerBoardGenerateRequest> request,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return request.map(body -> boardService.generate(body, principal));
    }

    @PostMapping("/validate")
    public Mono<BoardValidationResponse> validate(@Valid @RequestBody Mono<ClocktowerBoardValidateRequest> request) {
        return request.map(boardService::validate);
    }

    @PostMapping("/save")
    public Mono<ClocktowerBoardConfigResponse> save(@Valid @RequestBody Mono<ClocktowerBoardSaveRequest> request,
                                                    @AuthenticationPrincipal RbacPrincipal principal) {
        return request.map(body -> boardService.save(body, principal));
    }

    @GetMapping
    public Mono<List<ClocktowerBoardConfigResponse>> list() {
        return Mono.fromSupplier(boardService::list);
    }

    @DeleteMapping("/{boardId}")
    public Mono<Void> delete(@PathVariable Long boardId,
                             @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromRunnable(() -> boardService.delete(boardId, principal));
    }
}
