package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerInvestmentTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvestmentBusinessExceptionsToTheStandardEnvelope() {
        var response = handler.handleInvestmentException(new InvestmentException(
                        InvestmentErrorCode.INVALID_REQUEST,
                        "unsupported contract input"))
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-investment"))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVESTMENT_INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("unsupported contract input");
        assertThat(response.getBody().traceId()).isEqualTo("trace-investment");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void preservesInvestmentAccessAndResourceHttpSemantics() {
        assertThat(statusFor(InvestmentErrorCode.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusFor(InvestmentErrorCode.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusFor(InvestmentErrorCode.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
    }

    private org.springframework.http.HttpStatusCode statusFor(InvestmentErrorCode errorCode) {
        var response = handler.handleInvestmentException(new InvestmentException(errorCode, "rejected"))
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-status"))
                .block();
        assertThat(response).isNotNull();
        return response.getStatusCode();
    }
}
