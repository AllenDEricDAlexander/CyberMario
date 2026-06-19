package top.egon.mario.clocktower.board.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/boards")
@Validated
public class ClocktowerBoardController extends ClocktowerReactiveSupport {

    private final ClocktowerBoardService boardService;

    @PostMapping("/generate")
    public Mono<ApiResponse<ClocktowerBoardGenerateResponse>> generate(
            @Valid @RequestBody Mono<ClocktowerBoardGenerateRequest> request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return request.flatMap(body -> blocking(() -> boardService.generate(body, principal)));
    }

    @PostMapping("/validate")
    public Mono<ApiResponse<BoardValidationResponse>> validate(
            @Valid @RequestBody Mono<ClocktowerBoardValidateRequest> request) {
        return request.flatMap(body -> blocking(() -> boardService.validate(body)));
    }

    @PostMapping("/save")
    public Mono<ApiResponse<ClocktowerBoardConfigResponse>> save(
            @Valid @RequestBody Mono<ClocktowerBoardSaveRequest> request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return request.flatMap(body -> blocking(() -> boardService.save(body, principal)));
    }

    @GetMapping
    public Mono<ApiResponse<PageResult<ClocktowerBoardConfigResponse>>> list(
            @RequestParam(required = false) ClocktowerScriptCode scriptCode,
            @RequestParam(required = false) @Min(1) Integer playerCount,
            @RequestParam(required = false) Boolean valid,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        ClocktowerBoardQuery query = new ClocktowerBoardQuery(scriptCode, playerCount, valid);
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 200),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return blocking(() -> pageResult(boardService.list(query, pageRequest, principal)));
    }

    @DeleteMapping("/{boardId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long boardId,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> boardService.delete(boardId, principal));
    }

    private <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
