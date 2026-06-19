package top.egon.mario.clocktower.board;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.web.ClocktowerBoardController;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClocktowerBoardControllerTests {

    private final ClocktowerBoardService boardService = ClocktowerBoardTestFactory.service();

    @Test
    void generateBoardReturnsRequestedCandidateCount() {
        ClocktowerBoardGenerateRequest request = new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING, 5, 2, 2, 2, true, 2, List.of(), List.of(), "seed-1");

        ClocktowerBoardGenerateResponse response = boardService.generate(request, principal(1L));

        assertThat(response.candidates()).hasSize(2);
        assertThat(response.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.playerCount()).isEqualTo(5);
            assertThat(candidate.validation().valid()).isTrue();
            assertThat(candidate.roleCodes()).doesNotContain("BMR_TOWNSFOLK", "BMR_MINION", "BMR_DEMON");
            assertThat(candidate.roles()).extracting(role -> role.roleCode())
                    .containsExactlyElementsOf(candidate.roleCodes());
        });
    }

    @Test
    void listReturnsPagedBoardLibraryWithFilters() {
        ClocktowerBoardService service = mock(ClocktowerBoardService.class);
        ClocktowerBoardController controller = new ClocktowerBoardController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        ClocktowerBoardConfigResponse board = new ClocktowerBoardConfigResponse(7L, "CTB-00000007",
                ClocktowerScriptCode.TROUBLE_BREWING, 5, true, Instant.parse("2026-06-19T04:00:00Z"),
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), List.of(), null);
        when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(board), PageRequest.of(0, 20), 1));

        StepVerifier.create(controller.list(ClocktowerScriptCode.TROUBLE_BREWING, 5, true, 1, 20, principal(1L))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-board-list")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-board-list");
                    PageResult<ClocktowerBoardConfigResponse> page = response.data();
                    assertThat(page.page()).isEqualTo(1);
                    assertThat(page.size()).isEqualTo(20);
                    assertThat(page.records()).extracting(ClocktowerBoardConfigResponse::boardCode)
                            .containsExactly("CTB-00000007");
                })
                .verifyComplete();

        ArgumentCaptor<ClocktowerBoardQuery> queryCaptor = ArgumentCaptor.forClass(ClocktowerBoardQuery.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(queryCaptor.capture(), pageableCaptor.capture(), any());
        assertThat(queryCaptor.getValue().scriptCode()).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(queryCaptor.getValue().playerCount()).isEqualTo(5);
        assertThat(queryCaptor.getValue().valid()).isTrue();
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")));
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
