package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record WsTicketView(
        String ticket,
        Instant expiresAt) {
}
