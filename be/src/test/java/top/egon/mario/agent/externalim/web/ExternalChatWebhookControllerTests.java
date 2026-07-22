package top.egon.mario.agent.externalim.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.adapter.ExternalChatAdapterRegistry;
import top.egon.mario.agent.externalim.adapter.ExternalChatInboundAdapter;
import top.egon.mario.agent.externalim.adapter.ExternalWebhookRequest;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.externalim.runtime.ExternalChatIngressService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExternalChatWebhookControllerTests {

    private final ExternalChatAdapterRegistry registry = mock(ExternalChatAdapterRegistry.class);
    private final ExternalChatIngressService ingressService = mock(ExternalChatIngressService.class);
    private final ExternalChatInboundAdapter adapter = mock(ExternalChatInboundAdapter.class);
    private final ExternalChatWebhookController controller = new ExternalChatWebhookController(
            registry, ingressService, Schedulers.immediate());

    @Test
    void returnsNoContentOnlyAfterVerificationAndDurableAcceptance() {
        ExternalChatMessage message = new ExternalChatMessage("event-1", "message-1",
                ExternalChatPlatform.TELEGRAM, "main", "group-1", ExternalConversationType.GROUP,
                "telegram:main:group-1", new ExternalSender("sender-1", "Mario", ExternalSenderType.HUMAN),
                ExternalMessageType.TEXT, "hello", false, false, Instant.now());
        given(registry.requireInbound(ExternalChatPlatform.TELEGRAM)).willReturn(adapter);
        given(adapter.verifyAndNormalize(any(ExternalWebhookRequest.class)))
                .willReturn(Optional.of(message));

        StepVerifier.create(controller.receive("telegram", "main", headers(), Mono.just("{}".getBytes())))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();

        var ordered = inOrder(adapter, ingressService);
        ordered.verify(adapter).verifyAndNormalize(any(ExternalWebhookRequest.class));
        ordered.verify(ingressService).accept(eq(message), eq("trace-1"));
    }

    @Test
    void invalidSignatureReturnsUnauthorizedWithoutPersistence() {
        given(registry.requireInbound(ExternalChatPlatform.TELEGRAM)).willReturn(adapter);
        given(adapter.verifyAndNormalize(any(ExternalWebhookRequest.class))).willThrow(
                new ExternalChatException("EXTERNAL_CHAT_SIGNATURE_INVALID", "invalid signature"));

        StepVerifier.create(controller.receive("telegram", "main", headers(), Mono.just("{}".getBytes())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();

        verify(ingressService, never()).accept(any(), any());
    }

    @Test
    void authenticatedIgnoredEventReturnsNoContentWithoutPersistence() {
        given(registry.requireInbound(ExternalChatPlatform.QQ)).willReturn(adapter);
        given(adapter.verifyAndNormalize(any(ExternalWebhookRequest.class)))
                .willReturn(Optional.empty());

        StepVerifier.create(controller.receive("qq", "main", headers(),
                        Mono.just("{}".getBytes())))
                .assertNext(response -> assertThat(response.getStatusCode())
                        .isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();

        verify(ingressService, never()).accept(any(), any());
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "trace-1");
        return headers;
    }
}
