package top.egon.mario.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.RbacException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationErrors(WebExchangeBindException ex) {

        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errors", errors);

        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("request validation failed, fieldCount={}", errors.size());
                return ResponseEntity.badRequest().body(response);
            }));
        });
    }

    @ExceptionHandler(RbacException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleRbacException(RbacException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("rbac request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
            }));
        });
    }
}
