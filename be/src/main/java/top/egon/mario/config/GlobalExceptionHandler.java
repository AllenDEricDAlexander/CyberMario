package top.egon.mario.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.im.service.ImException;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rbac.service.RbacException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidationErrors(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("request validation failed, fieldCount={}", ex.getBindingResult().getFieldErrorCount());
                return validationResponse(message, traceId);
            }));
        });
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("; "));
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("method validation failed, violationCount={}", ex.getConstraintViolations().size());
                return validationResponse(message, traceId);
            }));
        });
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.toString() : error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("handler method validation failed, errorCount={}", ex.getAllErrors().size());
                return validationResponse(message, traceId);
            }));
        });
    }

    @ExceptionHandler(RbacException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleRbacException(RbacException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("rbac request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getDetailMessage(), traceId));
            }));
        });
    }

    @ExceptionHandler(RagException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleRagException(RagException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("rag request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
            }));
        });
    }

    @ExceptionHandler(NutritionException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNutritionException(NutritionException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("nutrition request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
            }));
        });
    }

    @ExceptionHandler(ImException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleImException(ImException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("im request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getDetailMessage(), traceId));
            }));
        });
    }

    @ExceptionHandler(AgentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAgentException(AgentException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("agent request rejected, code={}", ex.getCode());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
            }));
        });
    }

    @ExceptionHandler(ClocktowerException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleClocktowerException(ClocktowerException ex) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.just(TraceContext.withMdc(traceId, () -> {
                LogUtil.warn(log).log("clocktower request rejected, code={}", ex.getMessage());
                return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage(), ex.getMessage(), traceId));
            }));
        });
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(String message, String traceId) {
        String responseMessage = message == null || message.isBlank() ? "request validation failed" : message;
        return ResponseEntity.badRequest().body(ApiResponse.fail(VALIDATION_ERROR, responseMessage, traceId));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
