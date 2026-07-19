package top.egon.mario.investment.marketdata.provider.bitget;

import java.net.URI;
import java.time.Duration;

/**
 * Injectable blocking transport used only by durable market-data workers.
 */
@FunctionalInterface
public interface BitgetHttpTransport {

    Response get(URI uri, Duration timeout);

    record Response(int statusCode, String body) {
    }
}
