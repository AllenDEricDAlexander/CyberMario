package top.egon.mario.agent.externalim.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.adapter.ExternalChatAdapterRegistry;
import top.egon.mario.agent.externalim.adapter.ExternalWebhookRequest;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.runtime.ExternalChatIngressService;
import top.egon.mario.common.api.TraceContext;

import java.util.Locale;

@RestController
@RequestMapping("/api/external-im/webhooks")
public class ExternalChatWebhookController {

    private final ExternalChatAdapterRegistry adapterRegistry;
    private final ExternalChatIngressService ingressService;
    private final Scheduler blockingScheduler;

    public ExternalChatWebhookController(ExternalChatAdapterRegistry adapterRegistry,
                                         ExternalChatIngressService ingressService,
                                         Scheduler blockingScheduler) {
        this.adapterRegistry = adapterRegistry;
        this.ingressService = ingressService;
        this.blockingScheduler = blockingScheduler;
    }

    @PostMapping(path = "/{platform}/{connectorId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> receive(@PathVariable String platform,
                                              @PathVariable String connectorId,
                                              @RequestHeader HttpHeaders headers,
                                              @RequestBody Mono<byte[]> body) {
        return body.flatMap(bytes -> Mono.fromCallable(() -> {
                    ExternalChatPlatform selected = platform(platform);
                    var normalized = adapterRegistry.requireInbound(selected)
                            .verifyAndNormalize(new ExternalWebhookRequest(connectorId, headers, bytes));
                    normalized.ifPresent(message ->
                            ingressService.accept(message, TraceContext.resolve(headers)));
                    return ResponseEntity.noContent().<Void>build();
                }).subscribeOn(blockingScheduler))
                .onErrorMap(ExternalChatException.class, this::httpError);
    }

    private ExternalChatPlatform platform(String value) {
        try {
            return ExternalChatPlatform.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw new ExternalChatException("EXTERNAL_CHAT_PLATFORM_UNSUPPORTED",
                    "external chat platform is unsupported");
        }
    }

    private ResponseStatusException httpError(ExternalChatException error) {
        HttpStatus status = "EXTERNAL_CHAT_SIGNATURE_INVALID".equals(error.code())
                ? HttpStatus.UNAUTHORIZED
                : "EXTERNAL_CHAT_PLATFORM_UNSUPPORTED".equals(error.code())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, error.getMessage());
    }
}
