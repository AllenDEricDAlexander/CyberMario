package top.egon.mario.agent.externalim.adapter;

import org.springframework.http.HttpHeaders;

public record ExternalWebhookRequest(
        String connectorId,
        HttpHeaders headers,
        byte[] body
) {

    public ExternalWebhookRequest {
        headers = headers == null ? HttpHeaders.EMPTY : HttpHeaders.readOnlyHttpHeaders(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
