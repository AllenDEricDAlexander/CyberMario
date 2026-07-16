package top.egon.mario.clocktower.admin;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditFilterRequest;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditReportResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditSummaryResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.admin.service.ClocktowerManagementAuditService;
import top.egon.mario.clocktower.admin.web.AdminClocktowerAuditController;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClocktowerManagementAuditControllerTests {

    private final ClocktowerManagementAuditService auditService = mock(ClocktowerManagementAuditService.class);
    private final AdminClocktowerAuditController controller = controller(auditService);

    @Test
    void summaryAllowsEmptyFilterAndDelegatesToUnifiedAuditService() {
        ClocktowerAuditFilterRequest filter = new ClocktowerAuditFilterRequest();
        ClocktowerAuditSummaryResponse summary = new ClocktowerAuditSummaryResponse(2, 4, 8, 6, 20, 10, 3, 1);
        when(auditService.summary(any(), any())).thenReturn(summary);

        StepVerifier.create(controller.summary(filter, admin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower-summary")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-clocktower-summary");
                    assertThat(response.data()).isEqualTo(summary);
                })
                .verifyComplete();

        verify(auditService).summary(any(), any());
    }

    @Test
    void roomsAcceptsMultiValueFiltersAndReturnsServerPage() {
        ClocktowerAuditFilterRequest filter = new ClocktowerAuditFilterRequest();
        filter.setRoomIds(List.of(2L, 4L));
        filter.setGameIds(List.of(20L, 40L));
        ClocktowerAuditReportResponse.Room room = new ClocktowerAuditReportResponse.Room(
                2L, "ROOM2", "Room Two", "ACTIVE", "PUBLIC", 1L, 20, 4,
                Instant.parse("2026-06-24T07:00:00Z"), Instant.parse("2026-06-20T07:00:00Z"));
        when(auditService.rooms(any(), any(), any())).thenReturn(new PageImpl<>(
                List.of(room), PageRequest.of(1, 20), 21));

        StepVerifier.create(controller.rooms(filter, 2, 20, admin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower-rooms")))
                .assertNext(response -> {
                    PageResult<ClocktowerAuditReportResponse.Room> page = response.data();
                    assertThat(page.records()).containsExactly(room);
                    assertThat(page.page()).isEqualTo(2);
                    assertThat(page.total()).isEqualTo(21);
                })
                .verifyComplete();

        verify(auditService).rooms(any(), any(), any());
    }

    @Test
    void auditRoomDelegatesToAuditService() {
        ClocktowerRoomAuditResponse audit = new ClocktowerRoomAuditResponse(10L, "ROOM10", "Audit Room",
                "ACTIVE", "IN_GAME", "PUBLIC", 1L, 5, 20L, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        when(auditService.auditRoom(10L, admin())).thenReturn(audit);

        StepVerifier.create(controller.auditRoom(10L, admin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower-room-audit")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-clocktower-room-audit");
                    assertThat(response.data()).isEqualTo(audit);
                })
                .verifyComplete();
    }

    @Test
    void auditGameDelegatesToAuditService() {
        ClocktowerGameAuditResponse audit = new ClocktowerGameAuditResponse(20L, 10L, 1,
                "TROUBLE_BREWING", "RUNNING", "DAY", Instant.parse("2026-06-24T07:00:00Z"),
                null, List.of(), List.of(), List.of());
        when(auditService.auditGame(20L, admin())).thenReturn(audit);

        StepVerifier.create(controller.auditGame(20L, admin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower-game-audit")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-clocktower-game-audit");
                    assertThat(response.data()).isEqualTo(audit);
                })
                .verifyComplete();
    }

    @Test
    void messagesReturnsPagedAuditMessages() {
        ClocktowerChatMessageResponse message = new ClocktowerChatMessageResponse(30L, 40L, 2L, 1L,
                "TEXT", "private whisper", Instant.parse("2026-06-24T07:00:00Z"));
        when(auditService.messages(any(Long.class), any(), any())).thenReturn(new PageImpl<>(
                List.of(message), PageRequest.of(0, 20), 1));

        StepVerifier.create(controller.messages(40L, 1, 20, admin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower-chat-audit")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-clocktower-chat-audit");
                    PageResult<ClocktowerChatMessageResponse> page = response.data();
                    assertThat(page.records()).containsExactly(message);
                    assertThat(page.page()).isEqualTo(1);
                    assertThat(page.size()).isEqualTo(20);
                })
                .verifyComplete();

        verify(auditService).messages(any(Long.class), any(), any());
    }

    private static AdminClocktowerAuditController controller(ClocktowerManagementAuditService service) {
        AdminClocktowerAuditController controller = new AdminClocktowerAuditController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static RbacPrincipal admin() {
        return new RbacPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of(), "v1");
    }
}
