package top.egon.mario.investment.marketdata.provider.bitget;

import top.egon.mario.investment.marketdata.provider.MarketDataProviderException;
import top.egon.mario.investment.marketdata.provider.ProviderErrorCategory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * JDK HTTP transport with explicit request timeout and provider-neutral transport errors.
 */
final class JdkBitgetHttpTransport implements BitgetHttpTransport {

    private static final String PROVIDER_CODE = "BITGET";
    private static final long REQUEST_INTERVAL_NANOS = Duration.ofMillis(50).toNanos();

    private final HttpClient httpClient;
    private long nextRequestNanos;

    JdkBitgetHttpTransport(Duration timeout) {
        httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Response get(URI uri, Duration timeout) {
        acquireRequestSlot();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.RETRYABLE,
                    "Bitget request was interrupted", exception);
        } catch (IOException exception) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.RETRYABLE,
                    "Bitget request failed", exception);
        }
    }

    private void acquireRequestSlot() {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long requestAt = Math.max(now, nextRequestNanos);
            nextRequestNanos = requestAt + REQUEST_INTERVAL_NANOS;
            waitNanos = requestAt - now;
        }
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.RETRYABLE,
                    "Bitget request was interrupted before dispatch");
        }
    }
}
