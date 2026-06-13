package top.egon.mario.common.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ensures every WebFlux request has a trace identifier available to responses.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = TraceContext.resolve(exchange.getRequest().getHeaders());
        ServerWebExchange tracedExchange = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId)))
                .build();
        tracedExchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        return chain.filter(tracedExchange)
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, traceId));
    }

}
